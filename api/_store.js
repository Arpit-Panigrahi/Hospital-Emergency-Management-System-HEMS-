const fs = require('fs');
const path = require('path');
const os = require('os');

// ============================================================================
// 1. SEED DATA (Matches Oracle schema.sql)
// ============================================================================
const initialUsers = [
  { userId: 1, username: 'admin', passwordHash: 'admin123', fullName: 'ER Administrator', role: 'ADMIN' },
  { userId: 2, username: 'doctor', passwordHash: 'doctor123', fullName: 'Dr. Sarah Vance', role: 'DOCTOR' },
  { userId: 3, username: '25bce5654', passwordHash: '25bce5654', fullName: 'Student Lab Admin', role: 'ADMIN' }
];

const initialBeds = [
  { bedId: 101, bedType: 'ICU', isOccupied: 'N', patientId: null },
  { bedId: 102, bedType: 'ER_BAY', isOccupied: 'N', patientId: null },
  { bedId: 103, bedType: 'TRAUMA', isOccupied: 'N', patientId: null },
  { bedId: 104, bedType: 'ICU', isOccupied: 'N', patientId: null },
  { bedId: 105, bedType: 'ER_BAY', isOccupied: 'N', patientId: null }
];

const STORE_FILE = path.join(os.tmpdir(), 'hems_cloud_store_v1.json');

// In-memory cache
let inMemoryStore = null;

function formatTimestamp(date = new Date()) {
  const pad = n => String(n).padStart(2, '0');
  const yyyy = date.getFullYear();
  const mm = pad(date.getMonth() + 1);
  const dd = pad(date.getDate());
  const hh = pad(date.getHours());
  const mi = pad(date.getMinutes());
  const ss = pad(date.getSeconds());
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}:${ss}`;
}

function loadStore() {
  if (inMemoryStore) return inMemoryStore;

  try {
    if (fs.existsSync(STORE_FILE)) {
      const content = fs.readFileSync(STORE_FILE, 'utf-8');
      inMemoryStore = JSON.parse(content);
      return inMemoryStore;
    }
  } catch (e) {
    console.warn('Could not read store file, reinitializing:', e.message);
  }

  inMemoryStore = {
    users: [...initialUsers],
    beds: initialBeds.map(b => ({ ...b })),
    patients: []
  };

  saveStore(inMemoryStore);
  return inMemoryStore;
}

function saveStore(data) {
  inMemoryStore = data;
  try {
    fs.writeFileSync(STORE_FILE, JSON.stringify(data), 'utf-8');
  } catch (e) {
    console.warn('Could not persist store to disk:', e.message);
  }
}

// ============================================================================
// 2. POSTGRESQL POOL (If DATABASE_URL set)
// ============================================================================
let pgPool = null;

function getPgPool() {
  const connectionString = process.env.DATABASE_URL;
  if (!connectionString) return null;

  if (!pgPool) {
    try {
      const { Pool } = require('pg');
      pgPool = new Pool({
        connectionString,
        ssl: { rejectUnauthorized: false }
      });
      initPgDatabase(pgPool).catch(err => {
        console.error('PostgreSQL Init Error:', err.message);
      });
    } catch (e) {
      console.warn('PostgreSQL client unavailable. Using Serverless Storage Engine.');
      pgPool = null;
    }
  }
  return pgPool;
}

async function initPgDatabase(pool) {
  const client = await pool.connect();
  try {
    await client.query(`
      CREATE TABLE IF NOT EXISTS APP_USERS (
        USER_ID SERIAL PRIMARY KEY,
        USERNAME VARCHAR(30) UNIQUE NOT NULL,
        PASSWORD_HASH VARCHAR(50) NOT NULL,
        FULL_NAME VARCHAR(50) NOT NULL,
        ROLE VARCHAR(20) NOT NULL
      );

      CREATE TABLE IF NOT EXISTS EMERGENCY_PATIENT (
        PATIENT_ID INT PRIMARY KEY,
        NAME VARCHAR(50) NOT NULL,
        AGE INT NOT NULL,
        GENDER VARCHAR(10),
        TRIAGE_LEVEL VARCHAR(15),
        ADMISSION_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        STATUS VARCHAR(20) DEFAULT 'ADMITTED'
      );

      CREATE TABLE IF NOT EXISTS BED_ALLOCATION (
        BED_ID INT PRIMARY KEY,
        BED_TYPE VARCHAR(20),
        IS_OCCUPIED CHAR(1) DEFAULT 'N',
        PATIENT_ID INT
      );
    `);

    const uc = await client.query('SELECT COUNT(*) FROM APP_USERS');
    if (parseInt(uc.rows[0].count, 10) === 0) {
      for (const u of initialUsers) {
        await client.query(
          'INSERT INTO APP_USERS (USER_ID, USERNAME, PASSWORD_HASH, FULL_NAME, ROLE) VALUES ($1, $2, $3, $4, $5) ON CONFLICT DO NOTHING',
          [u.userId, u.username, u.passwordHash, u.fullName, u.role]
        );
      }
    }

    const bc = await client.query('SELECT COUNT(*) FROM BED_ALLOCATION');
    if (parseInt(bc.rows[0].count, 10) === 0) {
      for (const b of initialBeds) {
        await client.query(
          'INSERT INTO BED_ALLOCATION (BED_ID, BED_TYPE, IS_OCCUPIED, PATIENT_ID) VALUES ($1, $2, $3, $4) ON CONFLICT DO NOTHING',
          [b.bedId, b.bedType, b.isOccupied, b.patientId]
        );
      }
    }
  } finally {
    client.release();
  }
}

// ============================================================================
// 3. STORAGE API METHODS
// ============================================================================
async function findUser(username, password) {
  const pool = getPgPool();
  if (pool) {
    try {
      const res = await pool.query(
        'SELECT FULL_NAME, ROLE FROM APP_USERS WHERE USERNAME = $1 AND PASSWORD_HASH = $2',
        [username, password]
      );
      if (res.rows.length > 0) {
        return { fullName: res.rows[0].full_name, role: res.rows[0].role };
      }
      return null;
    } catch (e) {
      console.warn('Postgres query error, falling back:', e.message);
    }
  }

  const store = loadStore();
  const user = store.users.find(u => u.username === username && u.passwordHash === password);
  if (user) {
    return { fullName: user.fullName, role: user.role };
  }
  return null;
}

async function getPatients() {
  const pool = getPgPool();
  if (pool) {
    try {
      const res = await pool.query(`
        SELECT 
          PATIENT_ID as "patientId", 
          NAME as "name", 
          AGE as "age", 
          GENDER as "gender", 
          TRIAGE_LEVEL as "triage", 
          STATUS as "status", 
          TO_CHAR(ADMISSION_TIME, 'YYYY-MM-DD HH24:MI:SS') as "time"
        FROM EMERGENCY_PATIENT 
        ORDER BY PATIENT_ID ASC
      `);
      return res.rows;
    } catch (e) {
      console.warn('Postgres patients error, falling back:', e.message);
    }
  }

  const store = loadStore();
  return store.patients;
}

async function getBeds() {
  const pool = getPgPool();
  if (pool) {
    try {
      const res = await pool.query(`
        SELECT 
          BED_ID as "bedId", 
          BED_TYPE as "bedType", 
          IS_OCCUPIED as "isOccupied", 
          PATIENT_ID as "patientId" 
        FROM BED_ALLOCATION 
        ORDER BY BED_ID ASC
      `);
      return res.rows;
    } catch (e) {
      console.warn('Postgres beds error, falling back:', e.message);
    }
  }

  const store = loadStore();
  return store.beds;
}

async function admitPatient({ patientId, name, age, gender, triage, bedId }) {
  const pid = parseInt(patientId, 10);
  const bid = parseInt(bedId, 10);

  const pool = getPgPool();
  if (pool) {
    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      const patCheck = await client.query(
        'SELECT PATIENT_ID FROM EMERGENCY_PATIENT WHERE PATIENT_ID = $1 AND STATUS = $2',
        [pid, 'ADMITTED']
      );
      if (patCheck.rows.length > 0) {
        await client.query('ROLLBACK');
        return { success: false, error: 'Duplicate Patient', message: `Patient #${pid} is already actively admitted.` };
      }

      await client.query(`
        INSERT INTO EMERGENCY_PATIENT (PATIENT_ID, NAME, AGE, GENDER, TRIAGE_LEVEL, STATUS, ADMISSION_TIME)
        VALUES ($1, $2, $3, $4, $5, 'ADMITTED', CURRENT_TIMESTAMP)
      `, [pid, name, age, gender, triage]);

      const bedUpdate = await client.query(`
        UPDATE BED_ALLOCATION 
        SET IS_OCCUPIED = 'Y', PATIENT_ID = $1 
        WHERE BED_ID = $2 AND IS_OCCUPIED = 'N'
      `, [pid, bid]);

      if (bedUpdate.rowCount === 0) {
        await client.query('ROLLBACK');
        return { success: false, error: 'Bed Unavailable', message: `Bed #${bid} is unavailable or already occupied.` };
      }

      await client.query('COMMIT');
      return { success: true, message: `Patient #${pid} admitted & Bed #${bid} allocated successfully!` };
    } catch (e) {
      await client.query('ROLLBACK').catch(() => {});
      console.warn('Postgres admit error, falling back:', e.message);
    } finally {
      client.release();
    }
  }

  const store = loadStore();
  const bed = store.beds.find(b => b.bedId === bid);
  if (!bed) {
    return { success: false, error: 'Bed Not Found', message: `Bed #${bid} does not exist.` };
  }
  if (bed.isOccupied === 'Y') {
    return { success: false, error: 'Bed Occupied', message: `Bed #${bid} is unavailable or already occupied.` };
  }

  const dup = store.patients.find(p => p.patientId === pid && p.status === 'ADMITTED');
  if (dup) {
    return { success: false, error: 'Duplicate Patient', message: `Patient #${pid} is already actively admitted.` };
  }

  // Atomic state commit
  store.patients.push({
    patientId: pid,
    name,
    age: parseInt(age, 10),
    gender,
    triage,
    status: 'ADMITTED',
    time: formatTimestamp()
  });

  bed.isOccupied = 'Y';
  bed.patientId = pid;

  saveStore(store);
  return { success: true, message: `Patient #${pid} admitted & Bed #${bid} allocated successfully!` };
}

async function dischargePatient(patientId) {
  const pid = parseInt(patientId, 10);

  const pool = getPgPool();
  if (pool) {
    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      const patUpdate = await client.query(`
        UPDATE EMERGENCY_PATIENT 
        SET STATUS = 'DISCHARGED' 
        WHERE PATIENT_ID = $1 AND STATUS = 'ADMITTED'
      `, [pid]);

      if (patUpdate.rowCount === 0) {
        await client.query('ROLLBACK');
        return { success: false, error: 'Not Found', message: `Active admitted patient #${pid} not found.` };
      }

      await client.query(`
        UPDATE BED_ALLOCATION 
        SET IS_OCCUPIED = 'N', PATIENT_ID = NULL 
        WHERE PATIENT_ID = $1
      `, [pid]);

      await client.query('COMMIT');
      return { success: true, message: `Patient #${pid} discharged and bed released successfully!` };
    } catch (e) {
      await client.query('ROLLBACK').catch(() => {});
      console.warn('Postgres discharge error, falling back:', e.message);
    } finally {
      client.release();
    }
  }

  const store = loadStore();
  const patient = store.patients.find(p => p.patientId === pid && p.status === 'ADMITTED');
  if (!patient) {
    return { success: false, error: 'Not Found', message: `Active admitted patient #${pid} not found.` };
  }

  patient.status = 'DISCHARGED';
  const bed = store.beds.find(b => b.patientId === pid);
  if (bed) {
    bed.isOccupied = 'N';
    bed.patientId = null;
  }

  saveStore(store);
  return { success: true, message: `Patient #${pid} discharged and bed released successfully!` };
}

// ============================================================================
// 4. HTTP HELPERS
// ============================================================================
function applyCors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
}

function sendJson(res, statusCode, data) {
  applyCors(res);
  if (typeof res.status === 'function' && typeof res.json === 'function') {
    return res.status(statusCode).json(data);
  }
  res.writeHead(statusCode, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(data));
}

async function getRequestBody(req) {
  if (req.body && typeof req.body === 'object') return req.body;
  if (typeof req.body === 'string') {
    try { return JSON.parse(req.body); } catch (e) { return {}; }
  }
  return new Promise((resolve) => {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try { resolve(body ? JSON.parse(body) : {}); }
      catch (e) { resolve({}); }
    });
  });
}

module.exports = {
  findUser,
  getPatients,
  getBeds,
  admitPatient,
  dischargePatient,
  applyCors,
  sendJson,
  getRequestBody,
  formatTimestamp,
  getPgPool
};
