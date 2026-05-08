import { RefreshCw, Loader2, Check } from './AnimatedIcons';

export default function ProcessingSteps({ steps, currentStep }) {
  return (
    <div className="flex flex-col items-center justify-center gap-5 py-8">
      <RefreshCw className="h-8 w-8 text-accent animate-spin" />
      <div className="space-y-3 w-full max-w-xs">
        {steps.map((step, idx) => (
          <motion.div
            key={idx}
            initial={{ opacity: 0, x: -8 }}
            animate={{ opacity: currentStep >= idx ? 1 : 0.3, x: 0 }}
            transition={{ duration: 0.3, delay: idx * 0.05 }}
            className="flex items-center gap-2.5 px-3"
          >
            {currentStep > idx ? (
              <Check className="h-3.5 w-3.5 text-success shrink-0" />
            ) : currentStep === idx ? (
              <Loader2 className="h-3.5 w-3.5 text-accent animate-spin shrink-0" />
            ) : (
              <div className="h-3.5 w-3.5 rounded-full border border-border-default shrink-0" />
            )}
            <span className={`text-xs font-medium ${currentStep >= idx ? 'text-fg' : 'text-fg-subtle'}`}>
              {step.label}
            </span>
          </motion.div>
        ))}
      </div>
    </div>
  );
}
