import { describe, expect, it } from 'vitest';

describe('vitest setup smoke', () => {
  it('should be wired correctly', () => {
    // Arrange
    const sum = (a, b) => a + b;

    // Act
    const result = sum(2, 3);

    // Assert
    expect(result).toBe(5);
  });
});
