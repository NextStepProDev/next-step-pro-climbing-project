# This image PACKAGES a built artifact; it does not build one.
#
# `dist/` is produced by the lint-typecheck-build job (npm run build) and handed here as a
# workflow artifact, so what ships is byte-for-byte the directory that passed eslint, tsc,
# vitest, the America/New_York pass and Playwright — not a second, untested build of the same
# source. It also stops the bundle from being rebuilt once per platform: dist is HTML, CSS and
# JS, so the arm64 copy was Vite running under QEMU emulation to produce identical text files
# (~11 minutes of the docker job).
#
# The node version that builds this now lives only in .nvmrc + ci-frontend.yml — see the note
# there about why it is 24 and not 26.
#
# Building locally: run `npm run build` BEFORE `docker build .`.
FROM nginx:alpine
COPY dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
