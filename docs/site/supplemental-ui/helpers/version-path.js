'use strict'

// The path, from the site's root, of a page in the current page's version of the docs: (version-path 'examples/index.html')
// on a 3.1 page is /bootable/3.1/examples/index.html. The header's links use it, so they stay in the reader's version.
// A page outside any version, such as the 404 page, gets the latest version's.
module.exports = (target, { data: { root } }) => {
  const { page = {}, site = {} } = root
  const componentVersion = page.componentVersion ||
    ((site.components || {})[page.component ? page.component.name : 'bootable'] || {}).latest
  if (!componentVersion) return '/' + target
  const startPage = componentVersion.url
  return startPage.slice(0, startPage.lastIndexOf('/') + 1) + target
}
