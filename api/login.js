const { findUser, sendJson, getRequestBody, applyCors } = require('./_store');

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
  } catch (err) {
    console.error('Login error:', err);
    return sendJson(res, 500, { error: 'Internal Server Error', message: err.message });
  }
};
