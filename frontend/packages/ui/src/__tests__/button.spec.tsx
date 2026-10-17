import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { Button } from '../button.tsx';

describe('Button', () => {
    it('renders its children', () => {
        render(<Button>Click me</Button>);
        expect(screen.getByRole('button', { name: 'Click me' })).toBeInTheDocument();
    });

    it('applies the variant classes', () => {
        render(<Button variant="destructive">Delete</Button>);
        expect(screen.getByRole('button')).toHaveClass('bg-destructive');
    });

    it('forwards native props like onClick and disabled', async () => {
        const onClick = vi.fn();
        render(
            <Button disabled onClick={onClick}>
                Go
            </Button>,
        );
        const button = screen.getByRole('button');
        expect(button).toBeDisabled();
        button.click();
        expect(onClick).not.toHaveBeenCalled();
    });
});
