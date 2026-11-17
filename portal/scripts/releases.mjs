// Writes the Releases page from the CHANGELOG.md of the repository (Keep a Changelog), so the
// portal and the changelog never disagree.
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const changelog = readFileSync(resolve(import.meta.dirname, '..', '..', 'CHANGELOG.md'), 'utf8');
const body = changelog.replace(/^# .*\n+/, '').replace(/^(The format is based on .*\n(?:.*\n)*?)\n/m, '');

writeFileSync(
    resolve(import.meta.dirname, '..', 'src', 'content', 'docs', 'releases.md'),
    `---\ntitle: Releases\ndescription: Fabrio JVM release notes\n---\n\n${body}`,
);
