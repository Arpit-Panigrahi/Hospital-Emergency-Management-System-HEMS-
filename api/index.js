const {
  findUser,
  getPatients,
  getBeds,
  admitPatient,
  dischargePatient,
  sendJson,
  getRequestBody,
  applyCors,
  formatTimestamp,
  getPgPool
} = require('./_store');

module.exports = async function handler(req, res) {
  applyCors(res);

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const host = req.headers.host || 'localhost';
  const parsedUrl = new URL(req.url, `https://${host}`);
  let pathname = parsedUrl.pathname.replace(/\/+$/, '') || '/';

  if (!pathname.startsWith('/api')) {
    pathname = '/api' + (pathname === '/' ? '' : pathname);
  }

  try {
    if ((pathname === '/api' || pathname === '/api/health') && req.method === 'GET') {
      const pool = getPgPool();
      return sendJson(res, 200, {
        status: 'online',
        service: 'Hospital Emergency Management System (HEMS)',
        database: pool ? 'Cloud PostgreSQL' : 'Serverless Cloud State Engine',
        timestamp: formatTimestamp()
      });
    }

    if (pathname === '/api/login' && req.method === 'POST') {
      const body = await getRequestBody(req);
      const username = (body.username || '').trim();
      const password = (body.password || '').trim();

      if (!username || !password) {
        return sendJson(res, 400, {
          success: false,
          error: 'Missing Credentials',
          message: 'Username and password are required'
        });
      }

      const user = await findUser(username, password);
      if (user) {
        return sendJson(res, 200, {
          success: true,
          name: user.fullName,
          role: user.role
        });
      }

      return sendJson(res, 401, {
        success: false,
        error: 'Invalid Credentials',
        message: 'Invalid username or password'
      });
    }

    if (pathname === '/api/patients' && req.method === 'GET') {
      const patients = await getPatients();
      return sendJson(res, 200, patients);
    }

    if (pathname === '/api/beds' && req.method === 'GET') {
      const beds = await getBeds();
      return sendJson(res, 200, beds);
    }

    if (pathname === '/api/admit' && req.method === 'POST') {
      const body = await getRequestBody(req);
      const pid = parseInt(body.patientId, 10);
      const name = (body.name || '').trim();
      const age = parseInt(body.age, 10);
      const gender = (body.gender || '').trim();
      const triage = (body.triage || '').trim();
      const bid = parseInt(body.bedId, 10);

      if (!pid || !name || isNaN(age) || !gender || !triage || !bid) {
        return sendJson(res, 400, {
          success: false,
          error: 'Missing Fields',
          message: 'All admission fields are required.'
        });
      }

      const result = await admitPatient({ patientId: pid, name, age, gender, triage, bedId: bid });
      if (!result.success) {
        return sendJson(res, 400, result);
      }
      return sendJson(res, 200, result);
    }

    if (pathname === '/api/discharge' && req.method === 'POST') {
      const body = await getRequestBody(req);
      const pid = parseInt(body.patientId, 10);

      if (!pid) {
        return sendJson(res, 400, {
          success: false,
          error: 'Missing Patient ID',
          message: 'Patient ID is required.'
        });
      }

      const result = await dischargePatient(pid);
      if (!result.success) {
        return sendJson(res, 400, result);
      }
      return sendJson(res, 200, result);
    }

    return sendJson(res, 404, {
      error: 'Not Found',
      message: `Endpoint ${pathname} with method ${req.method} does not exist.`
    });

  } catch (error) {
    console.error('API Error:', error);
    return sendJson(res, 500, {
      error: 'Internal Server Error',
      message: error.message
    });
  }
};
