export default function OverviewLayout({ header, pageTabs, editBar, grid }) {
  return (
    <div className="flex flex-col py-4">
      {header}
      {pageTabs}
      {editBar}
      <div className="min-w-0 mt-3">{grid}</div>
    </div>
  );
}
