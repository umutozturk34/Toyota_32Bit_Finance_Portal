/**
 * @typedef {Object} OverviewLayoutProps
 * @property {React.ReactNode} header
 * @property {React.ReactNode} [editBar]
 * @property {React.ReactNode} grid
 */

/** @param {OverviewLayoutProps} props */
export default function OverviewLayout({ header, editBar, grid }) {
  return (
    <div className="flex flex-col py-4">
      {header}
      {editBar}
      <div className="min-w-0 mt-3">{grid}</div>
    </div>
  );
}
