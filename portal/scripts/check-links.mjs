// Fails the build when a page of dist/ links to a page, file or anchor that does not exist. Links
// in the content are relative, so the portal works under any base (PORTAL_BASE); this checks them
// after the build, together with the sidebar and the API reference. Dokka's own pages are skipped.
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';

const dist = resolve(import.meta.dirname, '..', 'dist');
// PORTAL_BASE as astro.config.mjs reads it: the environment first, then .env (written by CI).
const envFile = resolve(import.meta.dirname, '..', '.env');
const fromFile = existsSync(envFile) ? readFileSync(envFile, 'utf8').match(/^PORTAL_BASE=(.*)$/m)?.[1] : undefined;
const base = (process.env.PORTAL_BASE || fromFile || '/').replace(/\/?$/, '/');

const pages = [];
(function walk(dir) {
    for (const name of readdirSync(dir)) {
        const path = join(dir, name);
        if (statSync(path).isDirectory()) {
            if (relative(dist, path) !== 'api') walk(path);
        } else if (name.endsWith('.html')) pages.push(path);
    }
})(dist);

const ids = new Map();
const idsOf = (file) => {
    if (!ids.has(file)) ids.set(file, new Set([...readFileSync(file, 'utf8').matchAll(/\sid="([^"]+)"/g)].map((m) => m[1])));
    return ids.get(file);
};

const errors = [];
for (const page of pages) {
    const html = readFileSync(page, 'utf8');
    const url = '/' + relative(dist, page).split(sep).join('/').replace(/index\.html$/, '');
    for (const [, href] of html.matchAll(/<a\s[^>]*href="([^"]+)"/g)) {
        if (/^(https?:|mailto:|data:|#$)/.test(href)) continue;
        const [path, anchor] = href.split('#');
        let file = page;
        if (path) {
            const resolved = new URL(path, `http://portal${base}${url.slice(1)}`).pathname;
            if (!resolved.startsWith(base)) { errors.push(`${url}: ${href} (outside ${base})`); continue; }
            const target = join(dist, decodeURIComponent(resolved.slice(base.length)));
            file = [target, join(target, 'index.html')].find((candidate) => existsSync(candidate) && statSync(candidate).isFile());
            if (!file) { errors.push(`${url}: ${href}`); continue; }
        }
        if (anchor && file.endsWith('.html') && !idsOf(file).has(decodeURIComponent(anchor))) errors.push(`${url}: ${href} (no #${anchor})`);
    }
}

if (errors.length > 0) {
    console.error(`${errors.length} broken links:\n` + errors.join('\n'));
    process.exit(1);
}
console.log(`Links: ${pages.length} pages checked, no broken links`);
