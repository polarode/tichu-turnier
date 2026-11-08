// Catalog-coverage guard: production screens may not hardcode user-facing
// JSX text or user-facing attributes. Strings must come from the i18n
// catalogs (see shared/i18n).
const LETTER = /[A-Za-zÀ-ɏ]/;
const USER_FACING_ATTRIBUTES = new Set(['aria-label', 'title', 'alt', 'placeholder']);

export default {
    meta: { name: 'i18n' },
    rules: {
        'no-hardcoded-text': {
            create(context) {
                return {
                    JSXText(node) {
                        if (LETTER.test(node.value)) {
                            context.report({
                                node,
                                message:
                                    'User-facing text must come from the i18n catalog, not hardcoded JSX text.',
                            });
                        }
                    },
                    JSXAttribute(node) {
                        if (
                            node.name.type === 'JSXIdentifier' &&
                            USER_FACING_ATTRIBUTES.has(node.name.name) &&
                            node.value?.type === 'Literal' &&
                            LETTER.test(node.value.value)
                        ) {
                            context.report({
                                node,
                                message:
                                    'User-facing attribute text must come from the i18n catalog, not a hardcoded literal.',
                            });
                        }
                    },
                };
            },
        },
    },
};
