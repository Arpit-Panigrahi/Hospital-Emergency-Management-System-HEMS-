const { getBeds, sendJson, applyCors } = require('./_store');

module.exports = async function handler(req, res) {
  applyCors(res);
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  try {
    const beds = await getBeds();
    return sendJson(res, 200, beds);
  } catch (err) {
    console.error('Beds error:', err);
    return sendJson(res, 500, { error: 'Internal Server Error', message: err.message });
  }
};
