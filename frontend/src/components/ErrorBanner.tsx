export function ErrorBanner({ message }: { message: string }) {
  return (
    <div className="error-banner" role="alert">
      <span aria-hidden="true">⚠</span> {message}
    </div>
  );
}
