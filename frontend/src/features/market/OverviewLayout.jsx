/**
 * @typedef {Object} OverviewLayoutProps
 * @property {React.ReactNode} header
 * @property {React.ReactNode} editBar
 * @property {React.ReactNode} strip
 * @property {React.ReactNode} main
 * @property {React.ReactNode} news
 * @property {boolean} newsVisible
 */

/** @param {OverviewLayoutProps} props */
export default function OverviewLayout({ header, editBar, strip, main, news, newsVisible }) {
  const gridStyle = newsVisible
    ? { gridTemplateColumns: 'minmax(0, 1fr) 320px', gridTemplateAreas: '"header header" "editbar editbar" "strip strip" "main news"' }
    : { gridTemplateColumns: 'minmax(0, 1fr)', gridTemplateAreas: '"header" "editbar" "strip" "main"' };
  return (
    <div className="grid gap-3 py-4 lg:gap-3 max-lg:grid-cols-1" style={gridStyle}>
      <div style={{ gridArea: 'header' }}>{header}</div>
      {editBar && <div style={{ gridArea: 'editbar' }}>{editBar}</div>}
      <div style={{ gridArea: 'strip' }} className="overflow-x-auto">{strip}</div>
      <div style={{ gridArea: 'main' }} className="min-w-0">{main}</div>
      {newsVisible && (
        <div style={{ gridArea: 'news' }} className="min-w-0 max-lg:!col-span-full">{news}</div>
      )}
    </div>
  );
}
