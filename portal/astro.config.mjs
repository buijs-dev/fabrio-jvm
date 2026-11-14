import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import { loadEnv } from 'vite';

// Where the portal is served, from the environment or .env (written by CI, see portal.yml). The
// defaults are its own domain; until that is set up, CI builds with PORTAL_SITE=https://buijs-dev.github.io
// and PORTAL_BASE=/fabrio-jvm (repository variables).
const env = { ...loadEnv('production', process.cwd(), 'PORTAL_'), ...process.env };
const site = env.PORTAL_SITE || 'https://fabrio.buijs.dev';
const base = env.PORTAL_BASE || '/';

// The sections per profile (modules, errors, scenarios) come with the slice of that profile.
export default defineConfig({
    site,
    base,
    trailingSlash: 'always',
    integrations: [
        starlight({
            title: 'Fabrio',
            description: 'The opinionated framework on Rialto: libraries and code generation for Kotlin services on Quarkus',
            social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/buijs-dev/fabrio-jvm' }],
            editLink: { baseUrl: 'https://github.com/buijs-dev/fabrio-jvm/edit/develop/portal/' },
            sidebar: [
                { label: 'Start', items: [{ slug: 'index', label: 'Introduction' }, { slug: 'getting-started' }] },
                { label: 'Reference', items: [{ slug: 'releases' }] },
            ],
        }),
    ],
});
