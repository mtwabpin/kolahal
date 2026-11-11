// @title Template: API key (JavaScript)
// @desc A starting point for a script that needs an API key, in JavaScript: you set the key on the phone, never in the code
// @author Kolahal
// @version 1.0
// @every 6h
// @secret GITHUB_TOKEN api.github.com A GitHub token: github.com → Settings → Developer settings → Fine-grained tokens (no permissions needed)
// @tags template, javascript, github
// @requires 0.4.1
//
// The JavaScript version of template-api-key. It keeps your GitHub followers as its status line:
// pin it (`script pin template-api-key-js`) to see it at the top of home. Talks to: api.github.com.
//
// The @secret line names the key (GITHUB_TOKEN), the site it may be sent to, and what it is. You
// set the value on the phone: Scripts → tap this script → Secrets, or
// `script secret template-api-key-js GITHUB_TOKEN`. It is kept encrypted, never in the code, backups
// or the log, and a request that carries it anywhere but api.github.com stops the script.

// null until you set it: say how, instead of failing.
const token = secrets.get("GITHUB_TOKEN");
if (!token) {
  status("GitHub: set GITHUB_TOKEN");
  // A hint, muted: fmt.color takes one of the theme's colours by role.
  return log(fmt.color("muted", "Set GITHUB_TOKEN"), "Scripts → template-api-key-js → Secrets");
}

const r = await http.get("https://api.github.com/user", {
  Accept: "application/vnd.github+json",
  Authorization: `Bearer ${token}`,
});
if (r.status === 401) {
  status("GitHub: token refused");
  return log(fmt.color("bad", "Token refused"), "GitHub doesn't accept it: set GITHUB_TOKEN again");
}
if (!r.ok) throw new Error(`GitHub answered HTTP ${r.status}`);
const me = JSON.parse(r.body);

status(`GitHub ${me.login}: ${me.followers} followers`);
