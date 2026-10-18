import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

export default tseslint.config(
    { ignores: ['**/dist/**', '**/node_modules/**', '**/routeTree.gen.ts', '**/coverage/**'] },
    js.configs.recommended,
    ...tseslint.configs.recommended,
    {
        plugins: { 'react-hooks': reactHooks },
        rules: {
            ...reactHooks.configs.recommended.rules,
            '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
        },
    },
    {
        // Catalog-coverage guard: production screens may not hardcode user-facing
        // JSX text or user-facing attributes. Strings must come from the i18n
        // catalogs (see shared/i18n).
        files: ['**/src/**/*.{ts,tsx}'],
        ignores: ['**/__tests__/**', '**/*.spec.{ts,tsx}'],
        rules: {
            'no-restricted-syntax': [
                'error',
                {
                    selector: 'JSXText[value=/[A-Za-z\u00c0-\u024f]/]',
                    message:
                        'User-facing text must come from the i18n catalog, not hardcoded JSX text.',
                },
                {
                    selector:
                        'JSXAttribute[name.name=/^(aria-label|title|alt|placeholder)$/][value.value=/[A-Za-z\u00c0-\u024f]/]',
                    message:
                        'User-facing attribute text must come from the i18n catalog, not a hardcoded literal.',
                },
            ],
        },
    },
);
