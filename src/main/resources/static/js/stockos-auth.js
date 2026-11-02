// Browser-side Supabase Auth bridge. No secret keys or privileged operations here.
// The API authenticates the JWT and links it to a server-side operational account.
(function () {
  'use strict';
  const SESSION_KEY = 'stockos.supabase.session';
  const CONFIG_KEY = 'stockos.supabase.config';
  const MODE_KEY = 'stockos.auth.mode';
  let pendingRefresh = null;

  function readJson(key) {
    try { return JSON.parse(sessionStorage.getItem(key) || 'null'); }
    catch (_) { return null; }
  }
  function config() { return readJson(CONFIG_KEY); }
  function isSupabase() { return sessionStorage.getItem(MODE_KEY) === 'supabase'; }

  async function discover() {
    // The mock preview has no live Java backend; it may omit /api/auth/config.
    const response = await fetch('/api/auth/config', { cache: 'no-store' });
    if (!response.ok) throw new Error('Configuracao de autenticacao indisponivel');
    const result = await response.json();
    if (result.mode !== 'supabase') {
      sessionStorage.removeItem(MODE_KEY);
      sessionStorage.removeItem(CONFIG_KEY);
      sessionStorage.removeItem(SESSION_KEY);
      return { mode: 'legacy' };
    }
    if (!result.supabaseUrl || !result.publishableKey) {
      throw new Error('Supabase Auth nao configurado no backend');
    }
    const root = new URL(result.supabaseUrl);
    if (!(root.protocol === 'https:' || ['127.0.0.1', 'localhost'].includes(root.hostname))) {
      throw new Error('URL de Supabase invalida');
    }
    sessionStorage.setItem(MODE_KEY, 'supabase');
    sessionStorage.setItem(CONFIG_KEY, JSON.stringify({
      url: root.origin, publishableKey: result.publishableKey
    }));
    return { mode: 'supabase' };
  }

  async function authRequest(path, body, accessToken) {
    const cfg = config();
    if (!cfg) throw new Error('Configuracao de Supabase nao carregada');
    const response = await fetch(cfg.url + '/auth/v1/' + path, {
      method: 'POST',
      headers: Object.assign({
        apikey: cfg.publishableKey,
        'Content-Type': 'application/json'
      }, accessToken ? { Authorization: 'Bearer ' + accessToken } : {}),
      body: body == null ? '{}' : JSON.stringify(body)
    });
    if (!response.ok) throw new Error('Nao foi possivel autenticar ou renovar a sessao');
    return response.status === 204 ? null : response.json();
  }

  function saveSession(session) {
    if (!session || !session.access_token || !session.refresh_token || !session.expires_in) {
      throw new Error('Sessao de autenticacao invalida');
    }
    sessionStorage.setItem(SESSION_KEY, JSON.stringify({
      access_token: session.access_token,
      refresh_token: session.refresh_token,
      expires_at: Math.floor(Date.now() / 1000) + session.expires_in
    }));
  }

  async function signIn(email, password) {
    if (!isSupabase()) throw new Error('Modo Supabase nao habilitado');
    const result = await authRequest('token?grant_type=password', { email, password });
    saveSession(result);
    return getAccessToken();
  }

  async function getAccessToken() {
    if (!isSupabase()) return null;
    let session = readJson(SESSION_KEY);
    if (!session || !session.access_token || !session.refresh_token) return null;
    if (session.expires_at > Math.floor(Date.now() / 1000) + 60) return session.access_token;
    if (!pendingRefresh) {
      pendingRefresh = authRequest('token?grant_type=refresh_token', {
        refresh_token: session.refresh_token
      }).then(result => { saveSession(result); return result.access_token; })
        .catch(err => { sessionStorage.removeItem(SESSION_KEY); throw err; })
        .finally(() => { pendingRefresh = null; });
    }
    return pendingRefresh;
  }

  async function signOut() {
    const token = readJson(SESSION_KEY)?.access_token;
    if (token && config()) {
      try { await authRequest('logout', {}, token); } catch (_) {}
    }
    sessionStorage.removeItem(SESSION_KEY);
    sessionStorage.removeItem(MODE_KEY);
    sessionStorage.removeItem(CONFIG_KEY);
  }

  function clearSession() { sessionStorage.removeItem(SESSION_KEY); }
  window.StockosAuth = { discover, isSupabase, getAccessToken, signIn, signOut, clearSession };
})();
