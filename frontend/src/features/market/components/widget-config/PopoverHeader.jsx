export default function PopoverHeader({ Icon, title }) {
  return (
    <div className="flex items-center gap-2 mb-3">
      <span className="flex items-center justify-center w-6 h-6 rounded-md bg-accent/15 border border-accent/25">
        <Icon className="h-3 w-3 text-accent" />
      </span>
      <span className="font-display text-[12px] font-bold text-fg tracking-tight">{title}</span>
    </div>
  );
}
