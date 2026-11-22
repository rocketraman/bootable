'use strict'

// Publishes each docs version's API reference, the aggregated Dokka output of `./gradlew :dokkaGeneratePublicationHtml`,
// under api/ in that version, such as /bootable/3.1/api/, so the kdoc: macro's links resolve to the API the page was
// written for, on the same host, wherever the site is served from.
//
// next's comes from this checkout's build (config.dir). A release's comes from config.versionsDir/<version>, which the
// build fills by running Dokka at that release's tag: a version without one keeps its pages, with its API links
// unchecked and unpublished, and the build warns.
//
// The sidebar gets an API reference group, after Examples and before the last group, Comparison: All modules, and a
// link to each module's page.
//
// Each Dokka page gets the docs site's header in place of Dokka's own, and the docs theme over Dokka's styles
// (css/api.css), so the API reference reads as part of the site. The header is taken from the rendered home page,
// so it is the same markup as on the docs pages. So is the sidebar, in place of Dokka's: its API reference group lists
// All modules, then Dokka's own tree of the modules, their packages, and their types, which Dokka's
// navigation-loader.js fills in, expanded to the current page. The site's search
// replaces Dokka's: the home page's search scripts load on each API page too, and the search shows the docs' results
// and, from Dokka's own index, the API's (supplemental-ui/js/bootable.js).

const fs = require('node:fs')
const path = require('node:path')

const API_DIR = 'api'
const API_NAV_TITLE = 'API reference'
const COMPONENT = 'bootable'
const NEXT = 'next'

module.exports.register = function ({ config = {}, playbook }) {
  const logger = this.getLogger('dokka-extension')
  const nextDir = path.resolve(playbook.dir, config.dir || '../../build/dokka/html')
  const versionsDir = path.resolve(playbook.dir, config.versionsDir || './build/api-versions')
  // each version's Dokka output, for the kdoc: macro to check each link's target exists
  const dirFor = (version) => (version === NEXT ? nextDir : path.join(versionsDir, version))
  global.bootableDokka = { dirFor, apiDir: API_DIR }

  this.on('navigationBuilt', ({ contentCatalog, navigationCatalog }) => {
    const component = contentCatalog.getComponent(COMPONENT)
    for (const { version, url } of (component ? component.versions : [])) {
      const dir = dirFor(version)
      const navigation = navigationCatalog.getNavigation(COMPONENT, version)
      if (!navigation || !fs.existsSync(dir)) continue
      const apiDir = path.posix.join(path.posix.dirname(url), API_DIR)
      const link = (content, target) => ({ content, url: `${apiDir}/${target}`, urlType: 'internal' })
      const items = [link('All modules', 'index.html')].concat(apiModules(dir).map((m) => link(m, `${m}/index.html`)))
      // between the next-to-last group and the last, Comparison
      const last = navigation[navigation.length - 1]
      const before = navigation[navigation.length - 2]
      const order = last ? (before ? (before.order + last.order) / 2 : last.order - 0.5) : 0
      navigationCatalog.addTree(COMPONENT, version, { content: API_NAV_TITLE, items, order, root: true })
    }
  })

  this.on('beforePublish', ({ siteCatalog, contentCatalog }) => {
    const uiDir = (playbook.ui && playbook.ui.outputDir) || '_'
    const component = contentCatalog.getComponent(COMPONENT)
    for (const { version } of (component ? component.versions : [])) {
      const dir = dirFor(version)
      if (!fs.existsSync(dir)) {
        logger.warn(version === NEXT
          ? `no Dokka output in ${dir}: run ./gradlew :dokkaGeneratePublicationHtml first; next's API links are broken`
          : `no API reference for ${version} in ${dir}: run Dokka at its tag into there; its API links are broken`)
        continue
      }
      const home = contentCatalog.getById({ component: COMPONENT, version, module: 'ROOT', family: 'page', relative: 'index.adoc' })
      if (!home) continue
      const header = siteHeader(home)
      if (!header) logger.warn(`no header in ${version}'s home page: its API reference keeps Dokka's own`)
      const searchScripts = siteSearchScripts(home) || ''
      const nav = siteNav(home)
      if (!nav) logger.warn(`no sidebar in ${version}'s home page: its API reference keeps Dokka's own`)
      // the version's own directory, where its home page is: e.g. bootable/3.1
      const versionDir = path.posix.dirname(home.out.path)
      let count = 0
      for (const rel of walk(dir)) {
        const outPath = path.posix.join(versionDir, API_DIR, rel.split(path.sep).join('/'))
        let contents = fs.readFileSync(path.join(dir, rel))
        if (header && rel.endsWith('.html')) contents = themePage(contents.toString(), outPath, header, nav, searchScripts, uiDir)
        siteCatalog.addFile({ contents: Buffer.from(contents), out: { path: outPath } })
        count++
      }
      logger.info(`published ${count} Dokka files under /${versionDir}/${API_DIR}/`)
    }
  })
}

// The header of a rendered docs page, with its links resolved to paths from the site's root, so they can be made
// relative to each API page. The wiring switch has no code to switch here, so it's left out.
function siteHeader (page) {
  const match = page.contents.toString().match(/<header class="header">[\s\S]*?<\/header>/)
  if (!match) return
  const html = match[0]
    .replace(/\s*<label class="di-switch"[\s\S]*?<\/label>/, '')
    .replace(/ is-current/g, '')
    .replace('class="section-link section-link-api"', 'class="section-link section-link-api is-current"')
    // Dokka's scripts offset an anchor's scroll by #navigation-wrapper, and toggle the theme from #theme-toggle-button
    .replace('<header class="header"', '<header class="header" id="navigation-wrapper"')
    .replace('<button class="icon-button theme-toggle"', '<button class="icon-button theme-toggle" id="theme-toggle-button"')
  return fromRoot(html, page)
}

// The sidebar of a rendered docs page, resolved as the header is, with nothing current, and with its API reference
// group's module links in place of All modules' siblings left to Dokka's tree, in #sideMenu.
function siteNav (page) {
  const match = page.contents.toString().match(/<div class="nav-container"[\s\S]*?<\/aside>\s*<\/div>/)
  if (!match) return
  const apiGroup = new RegExp(`(<span class="nav-text">${API_NAV_TITLE}</span>\\s*<ul class="nav-list">\\s*<li[\\s\\S]*?</li>)[\\s\\S]*?</ul>`)
  const html = match[0]
    .replace(/ is-current-page| is-current-path/g, '')
    // an item opened to the home page; the menu's panel stays active, which shows it
    .replace(/(<li class="nav-item[^"]*?) is-active/g, '$1')
    // bootable.js opens and closes it on an API page, where the docs UI's script isn't loaded
    .replace('<div class="nav-container"', '<div class="nav-container" data-api')
    .replace(apiGroup, '$1</ul>\n<div id="sideMenu"></div>')
  return fromRoot(html, page)
}

// The Lunr extension's scripts in a rendered docs page, from lunr.js to the index, resolved as the header is.
function siteSearchScripts (page) {
  const match = page.contents.toString().match(/<script src="[^"]*\/lunr\.js"><\/script>[\s\S]*?search-index\.js"><\/script>/)
  return match && fromRoot(match[0], page)
}

// a page's relative URLs as {{root:<path from the site's root>}}, for an API page to make relative to itself
function fromRoot (html, page) {
  const from = path.posix.dirname(page.out.path)
  return html.replace(/(href|src|data-href|data-site-root-path|data-stylesheet)="([^"#:]+)(#[^"]*)?"/g, (_, attr, url, hash = '') =>
    `${attr}="{{root:${path.posix.normalize(path.posix.join(from, url))}}}${hash}"`)
}

function themePage (html, outPath, header, nav, searchScripts, uiDir) {
  const dokkaHeader = html.match(/<header class="navigation[\s\S]*?<\/header>/)
  if (!dokkaHeader) return html // a fragment, such as navigation.html
  const from = path.posix.dirname(outPath)
  const rel = (target) => path.posix.relative(from, target) || path.posix.basename(target)
  const toPage = (fragment) => fragment.replace(/\{\{root:([^}]+)\}\}/g, (_, target) => rel(target))
  // with the site's sidebar, its toggle, for a phone, in place of Dokka's contents toggle
  const toggle = nav ? '<button class="nav-toggle" type="button" aria-label="Toggle the navigation"></button>' : ''
  const ownHeader = toPage(header).replace('<div class="navbar-tools">', `<div class="navbar-tools">${toggle}`)
  const css = ['tokens', 'header', 'nav', 'api'].map((name) => `<link href="${rel(`${uiDir}/css/${name}.css`)}" rel="Stylesheet">`)
  // the API's index is All modules, the API reference group's first link
  const allModules = path.posix.basename(from) === API_DIR && path.posix.basename(outPath) === 'index.html'
  const ownNav = nav && toPage(nav).replace(/(<li class="nav-item)(" data-depth="1">\s*<a class="nav-link" href="index\.html">All modules)/,
    allModules ? '$1 is-current-page$2' : '$1$2')
  return html
    .replace(dokkaHeader[0], ownHeader)
    .replace(/<nav id="leftColumn"[\s\S]*?<\/nav>\s*<div id="resizer"[^>]*><\/div>/, (dokkaNav) => ownNav || dokkaNav)
    // Dokka's main.js is its search, which the site's replaces: left in, it would claim Ctrl+K too
    .replace(/<script[^>]*src="[^"]*scripts\/main\.js"[^>]*>\s*<\/script>/, '')
    // the docs site's theme choice, before the first paint: Dokka's dark styles key off .theme-dark, ours off data-theme
    .replace(/<script>const storage = localStorage\.getItem\("dokka-dark-mode"\)[\s\S]*?<\/script>/, THEME_INIT)
    .replace(/<title>([^<]*)<\/title>/, '<title>$1 · Bootable API</title>')
    .replace('</head>', `${css.join('\n')}\n<script defer src="${rel(`${uiDir}/js/bootable.js`)}"></script>\n</head>`)
    .replace(/<\/body>(?![\s\S]*<\/body>)/, `${toPage(searchScripts)}\n</body>`)
}

const THEME_INIT = `<script>(function () {
  var root = document.documentElement, theme
  try { theme = localStorage.getItem('bootable-theme') } catch (e) {}
  if (theme !== 'dark' && theme !== 'light') theme = matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  root.dataset.theme = theme
  root.classList.toggle('theme-dark', theme === 'dark')
})()</script>`

// the modules of a Dokka output: its directories with a page of their own, as opposed to its scripts and styles
function apiModules (dir) {
  return fs.readdirSync(dir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && fs.existsSync(path.join(dir, entry.name, 'index.html')))
    .map((entry) => entry.name)
    .sort()
}

function * walk (root, rel = '') {
  for (const entry of fs.readdirSync(path.join(root, rel), { withFileTypes: true })) {
    const entryRel = path.join(rel, entry.name)
    if (entry.isDirectory()) yield * walk(root, entryRel)
    else yield entryRel
  }
}
