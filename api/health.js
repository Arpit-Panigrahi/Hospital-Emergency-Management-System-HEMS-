const { sendJson, applyCors, formatTimestamp, getPgPool } = require('./_store');

module.exports = async function handler(req, res) {
  applyCors(res);
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const pool = getPgPool();
  return sendJson(res, 200, {
    status: 'online',
    service: 'Hospital Emergency Management System (HEMS)',
    database: pool ? 'Cloud PostgreSQL' : 'Serverless Cloud State Engine',
    timestamp: formatTimestamp()
  });
};
