import { useState, useLayoutEffect, useMemo, useRef } from 'react';
import { createPortal } from 'react-dom';
import { motion, AnimatePresence, LayoutGroup, useReducedMotion } from 'framer-motion';
import { Link } from 'react-router-dom';
import { ChevronDown, ChevronRight } from 'lucide-react';

function SectionDivider() {
  return (
    <div className="my-2.5 h-px mx-2 bg-gradient-to-r from-transparent via-border-default to-transparent" />
  );
}

function NavLeaf({ to, label, Icon, active, collapsed, isMobile, indented }) {
  // Rail (top-level) items keep their icon in a FIXED-WIDTH, left-anchored column (w-12 == the collapsed
  // content width: 4rem rail − 2×px-2 nav padding). The icon centers inside that box at a constant x in BOTH
  // states, so collapsing the rail can no longer drift it sideways. The label simply gets clipped by the row's
  // overflow-hidden as the width animates down — no justify-center toggle (which, while the label was still
  // exiting, used to center the icon in the still-wide row and snap it right). Indented sub-items never collapse,
  // so they keep the original inline layout.
  const rail = !indented;
  const reduceMotion = useReducedMotion();
  return (
    <Link
      to={to}
      title={collapsed && !isMobile ? label : undefined}
      className={`group relative flex items-center rounded-lg no-underline overflow-hidden transition-all duration-200 ease-out ${
        rail ? 'px-0 py-2' : 'gap-2.5 pl-3 pr-2 py-1.5'
      } ${
        active
          ? 'text-fg shadow-[0_4px_20px_-6px_rgba(99,102,241,0.35)]'
          : 'text-fg-muted hover:text-fg'
      }`}
    >
      {active && (
        <motion.span
          layoutId={reduceMotion ? undefined : 'sidebar-active'}
          className="absolute inset-0 rounded-lg bg-gradient-to-r from-accent/20 via-accent/10 to-accent/5 border border-accent/20"
          transition={{ type: 'spring', stiffness: 420, damping: 34 }}
        >
          <span
            className={`absolute left-0 top-1/2 -translate-y-1/2 w-[3px] rounded-r-full bg-accent shadow-[0_0_8px_rgba(99,102,241,0.7)] ${
              collapsed && !isMobile ? 'h-5' : indented ? 'h-4' : 'h-6'
            }`}
          />
        </motion.span>
      )}
      {!active && (
        <span className="absolute inset-0 rounded-lg bg-surface/0 group-hover:bg-surface/60 transition-colors duration-200" />
      )}
      {rail ? (
        <span className="relative flex items-center justify-center w-12 shrink-0">
          <Icon
            size={16}
            strokeWidth={1.6}
            className={`transition-all duration-200 ${
              active
                ? 'text-accent scale-110 drop-shadow-[0_0_6px_rgba(99,102,241,0.5)]'
                : 'group-hover:text-fg-muted group-hover:scale-105'
            }`}
          />
        </span>
      ) : (
        <Icon
          size={14}
          strokeWidth={1.6}
          className={`relative shrink-0 transition-all duration-200 ${
            active
              ? 'text-accent scale-110 drop-shadow-[0_0_6px_rgba(99,102,241,0.5)]'
              : 'group-hover:text-fg-muted group-hover:scale-105'
          }`}
        />
      )}
      <AnimatePresence initial={false}>
        {(!collapsed || isMobile) && (
          <motion.span
            initial={{ opacity: 0, x: -4 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -4 }}
            transition={{ duration: 0.16, ease: 'easeOut' }}
            className={`relative whitespace-nowrap ${rail ? 'pr-3' : ''} ${
              indented ? 'text-[12px]' : 'text-[13px] font-medium'
            } ${active ? 'font-semibold' : ''}`}
          >
            {label}
          </motion.span>
        )}
      </AnimatePresence>
    </Link>
  );
}

function NavGroupExpanded({ group, t, expanded, onToggle, isActive, hasActive, isMobile }) {
  const Icon = group.Icon;
  const label = t(group.labelKey);
  return (
    <div>
      <button
        type="button"
        onClick={onToggle}
        className={`w-full group relative flex items-center pl-0 pr-2 py-2 rounded-lg transition-all duration-200 ease-out border-none cursor-pointer bg-transparent ${
          hasActive ? 'text-fg' : 'text-fg-muted hover:text-fg'
        }`}
      >
        <span className={`absolute inset-0 rounded-lg transition-colors duration-200 ${
          hasActive ? 'bg-surface/40' : 'bg-surface/0 group-hover:bg-surface/40'
        }`} />
        <span className="relative flex items-center justify-center w-12 shrink-0">
          <Icon
            size={16}
            strokeWidth={1.6}
            className={`transition-all ${
              hasActive ? 'text-accent' : 'group-hover:text-fg-muted'
            }`}
          />
        </span>
        <span className={`relative flex-1 text-left text-[13px] font-semibold whitespace-nowrap ${
          hasActive ? 'text-fg' : ''
        }`}>
          {label}
        </span>
        {hasActive && !expanded && (
          <span className="relative h-1.5 w-1.5 rounded-full bg-accent shadow-[0_0_6px_rgba(99,102,241,0.6)]" />
        )}
        <motion.span
          animate={{ rotate: expanded ? 0 : -90 }}
          transition={{ type: 'spring', stiffness: 380, damping: 32 }}
          className="relative shrink-0 text-fg-subtle group-hover:text-fg-muted"
        >
          <ChevronDown size={14} />
        </motion.span>
      </button>
      <AnimatePresence initial={false}>
        {expanded && (
          <motion.div
            key="sub"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.22, ease: [0.32, 0.72, 0, 1] }}
            className="overflow-hidden"
          >
            <div className="relative ml-[18px] pl-3 mt-1 pb-1 space-y-0.5 border-l border-border-default/50">
              {group.items.map((item) => (
                <NavLeaf
                  key={item.to}
                  to={item.to}
                  label={item.label ?? t(item.labelKey)}
                  Icon={item.Icon}
                  active={isActive(item.to)}
                  collapsed={false}
                  isMobile={isMobile}
                  indented
                />
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function NavGroupCollapsed({ group, t, isActive, hasActive }) {
  const buttonRef = useRef(null);
  const closeTimerRef = useRef(null);
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState({ top: 0, left: 0 });
  const Icon = group.Icon;

  useLayoutEffect(() => {
    if (!open || !buttonRef.current) return undefined;
    const place = () => {
      const rect = buttonRef.current.getBoundingClientRect();
      setPos({ top: rect.top, left: rect.right + 8 });
    };
    place();
    // position:fixed does not follow the scrollable <nav> or a window resize, so recompute while open —
    // otherwise the portaled popover detaches at a stale top.
    window.addEventListener('scroll', place, true);
    window.addEventListener('resize', place);
    return () => {
      window.removeEventListener('scroll', place, true);
      window.removeEventListener('resize', place);
    };
  }, [open]);

  const openNow = () => {
    if (closeTimerRef.current) {
      clearTimeout(closeTimerRef.current);
      closeTimerRef.current = null;
    }
    setOpen(true);
  };

  const scheduleClose = () => {
    if (closeTimerRef.current) clearTimeout(closeTimerRef.current);
    closeTimerRef.current = setTimeout(() => setOpen(false), 90);
  };

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        title={open ? undefined : t(group.labelKey)}
        onMouseEnter={openNow}
        onMouseLeave={scheduleClose}
        onFocus={openNow}
        onBlur={scheduleClose}
        className={`group relative w-full flex items-center justify-center px-0 py-2 rounded-lg transition-all border-none cursor-pointer bg-transparent ${
          hasActive ? 'text-fg' : 'text-fg-muted hover:text-fg'
        }`}
      >
        <span className={`absolute inset-0 rounded-lg transition-colors duration-200 ${
          hasActive ? 'bg-surface/40' : 'bg-surface/0 group-hover:bg-surface/60'
        }`} />
        <Icon
          size={16}
          strokeWidth={1.6}
          className={`relative shrink-0 transition-all ${
            hasActive ? 'text-accent drop-shadow-[0_0_6px_rgba(99,102,241,0.5)]' : 'group-hover:text-fg-muted group-hover:scale-105'
          }`}
        />
        {hasActive && (
          <span className="absolute right-1 top-1 h-1.5 w-1.5 rounded-full bg-accent shadow-[0_0_6px_rgba(99,102,241,0.6)]" />
        )}
        <ChevronRight
          size={10}
          className="absolute right-0.5 top-1/2 -translate-y-1/2 text-fg-subtle opacity-40 group-hover:opacity-90 group-hover:text-accent transition pointer-events-none"
        />
      </button>
      {createPortal(
        <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, x: -10, scale: 0.96 }}
            animate={{ opacity: 1, x: 0, scale: 1 }}
            exit={{ opacity: 0, x: -10, scale: 0.96 }}
            transition={{ duration: 0.18, ease: [0.32, 0.72, 0, 1] }}
            onMouseEnter={openNow}
            onMouseLeave={scheduleClose}
            style={{
              position: 'fixed',
              top: pos.top,
              left: pos.left - 8,
              paddingLeft: 8,
              boxShadow: '0 20px 60px -15px rgba(0,0,0,0.65), 0 0 0 1px rgba(99,102,241,0.12), 0 0 80px -10px rgba(99,102,241,0.15)',
              background: 'var(--color-bg-deep)',
              backdropFilter: 'blur(20px)',
              WebkitBackdropFilter: 'blur(20px)',
              zIndex: 1000,
            }}
            className="rounded-2xl border border-border-default min-w-[16.25rem] max-w-[calc(100vw-1rem)] overflow-hidden"
          >
            <div className="h-px bg-gradient-to-r from-transparent via-accent/50 to-transparent" />
            <div className="px-4 pt-3 pb-2.5 flex items-center gap-2 border-b border-border-default/40">
              <div className="flex items-center justify-center w-6 h-6 rounded-md bg-accent/15">
                <Icon size={12} strokeWidth={2} className="text-accent" />
              </div>
              <span className="text-[10px] font-mono uppercase tracking-[0.22em] text-fg font-bold">
                {t(group.labelKey)}
              </span>
              <span className="ml-auto text-[9px] font-mono text-fg-subtle tabular-nums">
                {group.items.length}
              </span>
            </div>
            <div className="p-1.5 space-y-0.5">
              {group.items.map((item) => {
                const active = isActive(item.to);
                const ItemIcon = item.Icon;
                return (
                  <Link
                    key={item.to}
                    to={item.to}
                    onClick={() => setOpen(false)}
                    className={`group/item relative flex items-center gap-3 rounded-xl px-2 py-2 no-underline transition-all ${
                      active
                        ? 'bg-accent/12 shadow-[inset_0_0_0_1px_rgba(99,102,241,0.25)]'
                        : 'hover:bg-surface/60 hover:translate-x-0.5'
                    }`}
                  >
                    <span
                      className={`flex items-center justify-center w-8 h-8 rounded-lg transition-all shrink-0 ${
                        active
                          ? 'bg-accent/20 shadow-[0_2px_12px_-4px_rgba(99,102,241,0.5)]'
                          : 'bg-surface/60 group-hover/item:bg-accent/10'
                      }`}
                    >
                      <ItemIcon
                        size={14}
                        strokeWidth={1.7}
                        className={`transition-colors ${
                          active ? 'text-accent drop-shadow-[0_0_4px_rgba(99,102,241,0.6)]' : 'text-fg-muted group-hover/item:text-accent'
                        }`}
                      />
                    </span>
                    <div className="flex-1 min-w-0">
                      <div className={`text-[12.5px] font-semibold truncate ${
                        active ? 'text-fg' : 'text-fg group-hover/item:text-fg'
                      }`}>
                        {item.label ?? t(item.labelKey)}
                      </div>
                      {(item.sub || item.subKey) && (
                        <div className="text-[10px] text-fg-subtle font-mono mt-0.5 whitespace-nowrap truncate">
                          {item.sub ?? t(item.subKey, { defaultValue: '' })}
                        </div>
                      )}
                    </div>
                    {active && (
                      <span className="h-1.5 w-1.5 rounded-full bg-accent shadow-[0_0_6px_rgba(99,102,241,0.7)] shrink-0" />
                    )}
                    {!active && (
                      <ChevronRight size={12} className="text-fg-subtle opacity-0 group-hover/item:opacity-60 transition-opacity shrink-0" />
                    )}
                  </Link>
                );
              })}
            </div>
            <div className="h-px bg-gradient-to-r from-transparent via-border-default/50 to-transparent" />
          </motion.div>
        )}
      </AnimatePresence>,
        document.body,
      )}
    </>
  );
}

function SidebarNav({ structure, t, collapsed, isMobile, isActive, expandedGroups, toggleGroup, navId }) {
  const renderable = useMemo(() => {
    const out = [];
    structure.forEach((node, idx) => {
      if (idx > 0) out.push({ kind: 'divider', key: `div-${idx}` });
      out.push(node);
    });
    return out;
  }, [structure]);

  return (
    <LayoutGroup id={navId}>
      <nav className="flex-1 min-h-0 overflow-y-auto overflow-x-visible px-2 py-2 scrollbar-auto-hide">
      {renderable.map((node) => {
        if (node.kind === 'divider') {
          return <SectionDivider key={node.key} />;
        }
        if (node.kind === 'item') {
          return (
            <NavLeaf
              key={node.to}
              to={node.to}
              label={node.label ?? t(node.labelKey)}
              Icon={node.Icon}
              active={isActive(node.to)}
              collapsed={collapsed}
              isMobile={isMobile}
            />
          );
        }
        const hasActive = node.items.some((i) => isActive(i.to));
        if (collapsed && !isMobile) {
          return (
            <NavGroupCollapsed
              key={node.id}
              group={node}
              t={t}
              isActive={isActive}
              hasActive={hasActive}
            />
          );
        }
        return (
          <NavGroupExpanded
            key={node.id}
            group={node}
            t={t}
            expanded={expandedGroups.has(node.id)}
            onToggle={() => toggleGroup(node.id)}
            isActive={isActive}
            hasActive={hasActive}
            isMobile={isMobile}
          />
        );
      })}
      </nav>
    </LayoutGroup>
  );
}

export default SidebarNav;
