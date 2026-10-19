const { admitPatient, sendJson, getRequestBody, applyCors } = require('./_store');

module.exports = async function handler(req, res) {
  applyCors(res);
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.method !== 'POST') {
    return sendJson(res, 405, { error: 'Method Not Allowed', message: 'POST required' });
  }

  try {
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
  } catch (err) {
    console.error('Admit error:', err);
    return sendJson(res, 500, { error: 'Internal Server Error', message: err.message });
  }
};
