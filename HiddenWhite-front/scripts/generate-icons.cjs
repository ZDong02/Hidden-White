const fs = require('node:fs')
const path = require('node:path')
const { Resvg } = require('@resvg/resvg-js')
const directory = path.resolve(__dirname, '../static/icons')
for (const file of fs.readdirSync(directory).filter(file => file.endsWith('.svg'))) {
  const svg = fs.readFileSync(path.join(directory, file))
  const image = new Resvg(svg, { fitTo: { mode: 'width', value: 96 } }).render().asPng()
  fs.writeFileSync(path.join(directory, file.replace(/\.svg$/, '.png')), image)
}
