import { Link } from 'react-router-dom';
import { EmptyState } from '../components/Feedback.jsx';

export default function NotFoundPage() {
  return (
    <EmptyState title="Page not found" action={<Link className="btn btn-primary" to="/">Back to products</Link>}>
      The page you were looking for doesn&apos;t exist.
    </EmptyState>
  );
}
