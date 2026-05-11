import { motion } from 'framer-motion';
import { cardVariants } from '../../utils/animations';
import Card from '../card';

export default function AssetCard({ onClick, size = 'lg', children, className = '' }) {
  const padding = size === 'sm' ? 'md' : 'lg';
  const radius = size === 'sm' ? 'xl' : '2xl';
  return (
    <Card
      as={motion.div}
      variants={cardVariants}
      onClick={onClick}
      interactive
      radius={radius}
      padding={padding}
      className={`group ${className}`}
    >
      {children}
    </Card>
  );
}
