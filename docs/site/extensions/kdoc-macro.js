'use strict'

// kdoc:<module>:<fully qualified name>[text] links to the API reference for a class or top-level function, as Dokka
// published it under /api/ (see dokka.js), e.g.
//
//   kdoc:boot-db-jdbc:com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService[]
//
// renders `JdbcDataSourceService`, linked relative to the page, into its version's API reference, so the link works
// wherever the site is served from.
// The build warns when the Dokka page doesn't exist, so a renamed or moved type shows up as a broken link.

const fs = require('node:fs')
const path = require('node:path')

// Dokka's file names: each capital letter becomes a dash and its lower case, so JdbcDataSourceService becomes
// -jdbc-data-source-service, and jdbcTracing, jdbc-tracing
const mangle = (name) => name.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase())

function dokkaPath (module, fqn) {
  const segments = fqn.split('.')
  const firstType = segments.findIndex((s) => /^[A-Z]/.test(s))
  if (firstType === -1) {
    // a top-level function: the last segment, in its package
    const fn = segments.pop()
    return [module, segments.join('.'), mangle(fn) + '.html']
  }
  const pkg = segments.slice(0, firstType).join('.')
  return [module, pkg, ...segments.slice(firstType).map(mangle), 'index.html']
}

// The directory a page's version is published in, e.g. bootable/3.1: a ROOT page's module directory is the version's
// own, and another module's is one below it.
function versionDir (file) {
  if (!file || !file.out) return '.'
  const moduleDir = path.posix.normalize(path.posix.join(path.posix.dirname(file.out.path), file.out.moduleRootPath || '.'))
  return file.src && file.src.module === 'ROOT' ? moduleDir : path.posix.dirname(moduleDir)
}

module.exports.register = function (registry, context = {}) {
  const { file } = context
  registry.inlineMacro('kdoc', function () {
    this.positionalAttributes(['text'])
    this.process((parent, target, attrs) => {
      const colon = target.indexOf(':')
      if (colon < 0) throw new Error(`kdoc:${target}[]: expected kdoc:<module>:<fully qualified name>[]`)
      const module = target.slice(0, colon)
      const fqn = target.slice(colon + 1)
      const parts = dokkaPath(module, fqn)
      const { dirFor, apiDir = 'api' } = global.bootableDokka || {}
      const logger = parent.getDocument().getLogger()
      // the page's own version's API: next's from this checkout, a release's from its tag
      const version = file && file.src ? file.src.version : undefined
      const dir = dirFor && version !== undefined ? dirFor(version) : undefined
      if (dir && fs.existsSync(dir) && !fs.existsSync(path.join(dir, ...parts))) {
        logger.warn(`kdoc:${target}[]: no Dokka page at ${parts.join('/')}`)
      }
      // relative to the page: from its directory up to its version's, where api/ is, then into it
      const pageDir = file && file.out ? path.posix.dirname(file.out.path) : '.'
      const href = path.posix.relative(pageDir, path.posix.join(versionDir(file), apiDir, ...parts))
      const simpleName = fqn.slice(fqn.lastIndexOf('.') + 1)
      // given text has had its substitutions already, as the macro's line was converted before the macro ran, so only
      // the default, the simple name in monospace, needs them
      const text = attrs.text || parent.applySubstitutions(`\`${simpleName}\``)
      return this.createInline(parent, 'anchor', text, {
        type: 'link', target: href, attributes: { role: 'kdoc' },
      })
    })
  })
}
