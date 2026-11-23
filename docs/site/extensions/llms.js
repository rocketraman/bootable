'use strict'

// The docs for LLMs, as https://llmstxt.org/ describes:
//
// - each page as Markdown, at the page's URL with .md appended, such as concepts/lifecycle.html.md;
// - each version's llms.txt, such as /bootable/next/llms.txt: the site's summary, then a link to each page's Markdown,
//   with its description, a section for each group in the sidebar, in the sidebar's order;
// - each version's llms-full.txt: every page's Markdown, in the same order, in one file;
// - /llms.txt and /llms-full.txt, at the site's root: the default version's, the newest release, as the site's start
//   page is.
//
// The Markdown is made from each page's HTML once it is converted, so it has what the reader sees: the included
// snippets, the module tables and install snippets, and the API links. A tab set, such as the code in each wiring style,
// becomes each tab's content under its label, and an install with a telemetry variant has both listings, each labelled.
//
// Links are absolute, from the playbook's site.url, so the files can be read on their own: a link to a docs page goes to
// its Markdown, and any other link on the site, such as to the API reference, to its HTML.
//
// Each page's Markdown file name is its page-markdown attribute, which the UI's head links to (head-meta.hbs).

const path = require('node:path')
const TurndownService = require('turndown')

const COMPONENT = 'bootable'
const API_NAV_TITLE = 'API reference'
const ADMONITIONS = ['note', 'tip', 'important', 'caution', 'warning']

module.exports.register = function ({ config = {}, playbook }) {
  const logger = this.getLogger('llms-extension')
  const siteUrl = (playbook.site.url || '').replace(/\/$/, '')
  if (!siteUrl) logger.warn('no site.url in the playbook: llms.txt links are relative to the site\'s root')
  // each page's Markdown, by its URL, such as /bootable/next/concepts/lifecycle.html
  const markdown = new Map()

  this.on('documentsConverted', ({ contentCatalog }) => {
    const pageUrls = new Set(contentCatalog.getPages((page) => page.out).map((page) => page.pub.url))
    for (const page of contentCatalog.getPages((page) => page.out && page.asciidoc)) {
      const body = toMarkdown(page.contents.toString(), page.pub.url, pageUrls, siteUrl)
      const at = `${page.src.component}:${page.src.module}:${page.src.relative}`
      for (const problem of leftovers(body)) logger.warn(`${at}: its Markdown still has ${problem}`)
      const description = page.asciidoc.attributes.description || firstSentence(body)
      const title = page.asciidoc.doctitle || page.title
      const head = [`# ${plain(title)}`, page.asciidoc.attributes.description ? `> ${description}` : ''].filter(Boolean)
      markdown.set(page.pub.url, { page, title: plain(title), description, contents: `${head.join('\n\n')}\n\n${body}\n` })
      page.asciidoc.attributes['page-markdown'] = `${path.posix.basename(page.out.path)}.md`
    }
  })

  // the sidebar, for llms.txt's sections, with the API reference group the dokka extension adds
  let navigationCatalog
  this.on('navigationBuilt', (variables) => {
    navigationCatalog = variables.navigationCatalog
  })

  this.on('beforePublish', ({ siteCatalog, contentCatalog }) => {
    const component = contentCatalog.getComponent(COMPONENT)
    if (!component) return
    const latest = component.latest
    const absolute = (url) => `${siteUrl}${url}`
    const versionDir = (version) => path.posix.dirname(version.url)
    const addFile = (out, contents) => siteCatalog.addFile({ contents: Buffer.from(contents), out: { path: out.replace(/^\//, '') } })

    for (const entry of markdown.values()) addFile(`${entry.page.pub.url}.md`, entry.contents)

    for (const version of component.versions) {
      const navigation = [...(navigationCatalog.getNavigation(COMPONENT, version.version) || [])]
        .sort((a, b) => a.order - b.order)
      const listed = new Set()
      const sections = []
      for (const menu of navigation) {
        if (menu.content === API_NAV_TITLE) continue
        const entries = []
        collect(menu.items || [], '', (title, url) => {
          const entry = markdown.get(url.split('#')[0])
          if (!entry || listed.has(entry)) return
          listed.add(entry)
          entries.push({ ...entry, navTitle: title })
        })
        if (entries.length) sections.push({ title: plain(menu.content || component.title), entries })
      }
      // a page the sidebar doesn't list, so a reader of llms.txt still finds it
      const unlisted = [...markdown.values()]
        .filter((entry) => entry.page.src.version === version.version && !listed.has(entry))
      for (const entry of unlisted) {
        logger.warn(`${entry.page.src.module}:${entry.page.src.relative} isn't in the sidebar: llms.txt lists it under Other pages`)
      }
      if (unlisted.length) sections.push({ title: 'Other pages', entries: unlisted.map((entry) => ({ ...entry, navTitle: entry.title })) })

      const dir = versionDir(version)
      const start = markdown.get(version.url)
      const optional = []
      const api = navigation.find((menu) => menu.content === API_NAV_TITLE)
      if (api && api.items && api.items[0]) {
        optional.push(`- [API reference](${absolute(api.items[0].url)}): every module's API, from its KDoc, as HTML`)
      }
      optional.push(`- [All of these pages in one file](${absolute(`${dir}/llms-full.txt`)})`)
      for (const other of component.versions.filter((v) => v !== version)) {
        optional.push(`- [Bootable ${other.displayVersion}](${absolute(`${versionDir(other)}/llms.txt`)}): the docs of another version`)
      }
      if (config.changelog) optional.push(`- [Changelog](${config.changelog}): each release's changes`)

      const index = [
        `# ${component.title}`,
        start ? `> ${firstSentence(start.contents.replace(/^# .*\n+/, ''))}` : '',
        [aboutVersion(version), config.details && config.details.trim()].filter(Boolean).join('\n'),
        ...sections.map(({ title, entries }) => [
          `## ${title}`,
          '',
          ...entries.map(({ page, navTitle, description }) =>
            `- [${navTitle}](${absolute(`${page.pub.url}.md`)})${description ? `: ${oneLine(description)}` : ''}`),
        ].join('\n')),
        `## Optional\n\n${optional.join('\n')}`,
      ].filter(Boolean).join('\n\n') + '\n'

      const full = sections.flatMap(({ entries }) => entries)
        .map(({ page, contents }) => contents.replace(/^(# .*\n)/, `$1\nSource: ${absolute(page.pub.url)}\n`))
        .join('\n---\n\n')

      addFile(`${dir}/llms.txt`, index)
      addFile(`${dir}/llms-full.txt`, full)
      if (version === latest) {
        addFile('llms.txt', index)
        addFile('llms-full.txt', full)
      }
      logger.info(`llms.txt for ${version.version}: ${listed.size + unlisted.length} pages; llms-full.txt: ${Math.round(Buffer.byteLength(full) / 1024)} KiB`)
    }
  })
}

// which version the docs describe, and the release their install snippets use
function aboutVersion (version) {
  const release = version.asciidoc && version.asciidoc.attributes && version.asciidoc.attributes['bootable-version']
  const which = version.prerelease
    ? `the next version of Bootable, in progress and not yet released${release ? ` (${release})` : ''}`
    : `Bootable ${version.displayVersion}${release ? `, whose install snippets use release ${release}` : ''}`
  return `These docs describe ${which}.`
}

// each item of a sidebar tree that links to a page, its title prefixed with its parents', such as
// "Database: Drivers"
function collect (items, prefix, add) {
  for (const item of items) {
    const title = plain(item.content || '')
    const full = prefix ? `${prefix}: ${title}` : title
    if (item.url && item.urlType === 'internal') add(full, item.url)
    collect(item.items || [], full, add)
  }
}

// a page's converted HTML, the article without its title, as Markdown
function toMarkdown (html, pageUrl, pageUrls, siteUrl) {
  const service = new TurndownService({ headingStyle: 'atx', codeBlockStyle: 'fenced', bulletListMarker: '-' })
  const md = (node) => service.turndown(node.innerHTML).trim()
  const hasClass = (node, name) => typeof node.className === 'string' && node.className.split(/\s+/).includes(name)
  const all = (node, selector) => Array.prototype.slice.call(node.querySelectorAll(selector))

  // a heading's self link, a tab set's labels, which each panel says instead, and a callout's marker: in a code block,
  // its number, (1), stays, as the text after the marker. A rule, not remove(): Turndown tries its rules before what
  // it removes, and a link with no text isn't blank to it
  service.addRule('dropped', {
    filter: (node) => (node.nodeName === 'A' && hasClass(node, 'anchor')) || hasClass(node, 'tablist') ||
      (node.nodeName === 'I' && hasClass(node, 'conum')),
    replacement: () => '',
  })
  // a list item, its marker followed by one space, and its content indented to match
  service.addRule('list-item', {
    filter: 'li',
    replacement: (content, node) => {
      const list = node.parentNode
      const marker = list.nodeName === 'OL'
        ? `${Number(list.getAttribute('start') || 1) + Array.prototype.indexOf.call(list.children, node)}. `
        : '- '
      const body = content.replace(/^\n+/, '').replace(/\n+$/, '').replace(/\n/g, `\n${' '.repeat(marker.length)}`)
      return `${marker}${body}${node.nextSibling ? '\n' : ''}`
    },
  })

  service.addRule('tabpanel', {
    filter: (node) => hasClass(node, 'tabpanel'),
    replacement: (content, node) => {
      const tab = node.ownerDocument.getElementById(node.getAttribute('aria-labelledby'))
      return `\n\n**${tab ? tab.textContent.trim() : 'Tab'}:**\n\n${content.trim()}\n\n`
    },
  })
  service.addRule('install-variant', {
    filter: (node) => hasClass(node, 'install-plain') || hasClass(node, 'install-otel'),
    replacement: (content, node) =>
      `\n\n${hasClass(node, 'install-otel') ? 'With telemetry' : 'Without telemetry'}:\n\n${content.trim()}\n\n`,
  })
  // a code block's callouts, each after its number, as in the code
  service.addRule('callouts', {
    filter: (node) => hasClass(node, 'colist'),
    replacement: (content, node) => {
      const rows = all(node, 'tr').map((row) => {
        const [marker, text] = all(row, 'td')
        return `- (${marker.textContent.trim()}) ${md(text).replace(/\n+/g, ' ')}`
      })
      return `\n\n${rows.join('\n')}\n\n`
    },
  })
  service.addRule('admonition', {
    filter: (node) => hasClass(node, 'admonitionblock'),
    replacement: (content, node) => {
      const kind = ADMONITIONS.find((name) => hasClass(node, name)) || 'note'
      const text = node.querySelector('td.content')
      const body = `**${kind[0].toUpperCase()}${kind.slice(1)}:** ${text ? md(text) : content.trim()}`
      return `\n\n${body.split('\n').map((line) => `> ${line}`.trimEnd()).join('\n')}\n\n`
    },
  })
  // a block's title, such as a listing's file name
  service.addRule('block-title', {
    filter: (node) => node.nodeName === 'DIV' && hasClass(node, 'title'),
    replacement: (content) => `\n\n*${content.trim()}*\n\n`,
  })
  service.addRule('description-list', {
    filter: ['dt', 'dd'],
    replacement: (content, node) => `\n\n${node.nodeName === 'DT' ? `**${content.trim()}**` : content.trim()}\n\n`,
  })
  // a table as a GitHub table: a cell spanning columns, such as a group's title in a module table, is in the first
  // column, and the rest are empty
  service.addRule('table', {
    filter: 'table',
    replacement: (content, node) => {
      const rows = all(node, 'tr').map((row) => all(row, 'th, td').flatMap((cell) => {
        const text = md(cell).replace(/\|/g, '\\|').replace(/ *\n+/g, '<br>')
        const span = Number(cell.getAttribute('colspan') || 1)
        return [cell.nodeName === 'TH' && text && !row.parentNode.nodeName.match(/THEAD/) ? `**${text}**` : text,
          ...Array(span - 1).fill('')]
      }))
      if (!rows.length) return ''
      const columns = Math.max(...rows.map((row) => row.length))
      const line = (cells) => `| ${[...cells, ...Array(columns - cells.length).fill('')].join(' | ')} |`
      const [header, ...body] = node.querySelector('thead') ? rows : [Array(columns).fill(''), ...rows]
      return `\n\n${[line(header), line(Array(columns).fill('---')), ...body.map(line)].join('\n')}\n\n`
    },
  })
  service.addRule('link', {
    filter: (node) => node.nodeName === 'A' && node.getAttribute('href') && !hasClass(node, 'anchor'),
    replacement: (content, node) => {
      const href = linkTo(node.getAttribute('href'), pageUrl, pageUrls, siteUrl)
      const text = content.trim() || href
      return `[${text}](${href})`
    },
  })

  return service.turndown(html).replace(/^[ \t]+$/gm, '').replace(/\n{3,}/g, '\n\n').trim()
}

// a link's target, absolute: a docs page's Markdown, or, for anything else on the site, its HTML
function linkTo (href, pageUrl, pageUrls, siteUrl) {
  if (/^[a-z][a-z0-9+.-]*:/i.test(href)) return href
  const url = new URL(href, `https://site.invalid${pageUrl}`)
  const target = pageUrls.has(url.pathname) ? `${url.pathname}.md` : url.pathname
  return `${siteUrl}${target}${url.hash}`
}

// the first sentence of a page's Markdown, after its headings, as a description: its first paragraph's text
function firstSentence (markdown) {
  const paragraph = markdown.split(/\n\n+/).find((block) => /^[A-Za-z*_`[]/.test(block) && !block.startsWith('**'))
  if (!paragraph) return ''
  const text = oneLine(paragraph)
  const match = text.match(/^.+?[.!?](?=\s|$)/)
  return match ? match[0] : text
}

function oneLine (text) {
  return text.replace(/\s*\n\s*/g, ' ').trim()
}

// a title's text, without the HTML a title can have, such as <code>
function plain (html) {
  return String(html).replace(/<code>(.*?)<\/code>/g, '`$1`').replace(/<[^>]+>/g, '')
    .replace(/&#8217;/g, '’').replace(/&#8212;/g, '—').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
}

// markup the Markdown shouldn't have, outside its code blocks, from HTML the rules above don't handle
function leftovers (markdown) {
  const prose = markdown.replace(/^```[\s\S]*?^```/gm, '')
  return [...new Set(prose.match(/<\/?(div|table|span|pre|i|b)\b[^>]*>/g) || [])]
}
