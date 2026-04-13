export const formatBistSymbol = (displayName) => {
  if (displayName.endsWith('.IS')) {
    return displayName;
  }
  return `${displayName}.IS`;
};
