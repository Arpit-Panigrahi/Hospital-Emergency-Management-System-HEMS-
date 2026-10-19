const { getPatients, sendJson, applyCors } = require('./_store');

module.exports = async function handler(req, res) {
  applyCors(res);
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  try {
    const patients = await getPatients();
    return sendJson(res, 200, patients);
  } catch (err) {
    console.error('Patients error:', err);
    return sendJson(res, 500, { error: 'Internal Server Error', message: err.message });
  }
};
