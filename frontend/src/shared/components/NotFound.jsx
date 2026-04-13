import { useNavigate } from 'react-router-dom';

export default function NotFound() {
  const navigate = useNavigate();

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
      <p className="text-6xl font-mono font-bold text-fg-muted">404</p>
      <p className="text-lg text-fg-muted">Sayfa bulunamadı</p>
      <button
        onClick={() => navigate('/')}
        className="mt-2 px-4 py-2 rounded-lg bg-accent text-white text-sm font-medium hover:opacity-90 transition-opacity"
      >
        Ana Sayfaya Dön
      </button>
    </div>
  );
}
