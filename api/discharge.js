const { dischargePatient, sendJson, getRequestBody, applyCors } = require('./_store');

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
  } catch (err) {
    console.error('Discharge error:', err);
    return sendJson(res, 500, { error: 'Internal Server Error', message: err.message });
  }
};
