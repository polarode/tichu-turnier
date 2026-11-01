# Examples

Bad → good pairs for the rules in [SKILL.md](SKILL.md), written in this repo's stack. Where the repo already has a canonical implementation, the example points at it: copy that file's pattern instead of the snippet.

## Action on a non-button

```tsx
// Bad: no role, no keyboard access, no accessible name
<div onClick={onDelete}><TrashIcon /></div>

// Good: primitive button, icon hidden, name from the catalog
<Button variant="ghost" size="icon" aria-label={t('teams.delete')} onClick={onDelete}>
    <TrashIcon aria-hidden="true" />
</Button>
```

`size="icon"` is 36px. On player-facing screens add `className="size-11"` to reach the 44px touch target.

## Form field with validation

Canonical: `features/auth/components/login-form.component.tsx`. It uses `FormField` (label, `aria-invalid`, `aria-describedby` and a `role="alert"` message wired up) and `FormAlert` for form-level server errors.

```tsx
// Bad: placeholder as label, error shown by colour only, not linked to the input
<input placeholder="E-Mail" className={errors.email ? 'border-red-500' : ''} />

// Good
<FormField
    id="email"
    type="email"
    autoComplete="email"
    label={t('auth.email')}
    error={form.formState.errors.email?.message}
    {...form.register('email')}
/>
```

The submit button shows progress in its text and is disabled while pending:

```tsx
<Button type="submit" disabled={login.isPending}>
    {login.isPending ? t('login.pending') : t('login.submit')}
</Button>
```

## Status shown by colour only

```tsx
// Bad: a red dot means "Beschiss" only to sighted users who know the code
<span className="size-2 rounded-full bg-destructive" />

// Good: the badge carries text; colour reinforces it
<Badge variant="warning">{t('rounds.game.beschiss')}</Badge>
```

## The four states of an async view

Canonical: `features/tournaments/components/rounds-section.component.tsx`.

```tsx
const query = useRounds(tournamentId);

if (query.isPending) return <LoadingIndicator />;          // role="status"
if (query.isError) {
    return (
        <p role="alert" className="text-sm text-destructive">
            {localizedErrorMessage(query.error, t)}
        </p>
    );
}
if (query.data.length === 0) {
    // Empty: say why, and what happens next
    return <p className="text-sm text-muted-foreground">{t('rounds.empty')}</p>;
}
return <RoundList rounds={query.data} />;
```

A component may render nothing for loading or empty when its parent already explains the state; say so in the component's doc comment.

## Mobile-first layout

```tsx
// Bad: desktop grid that overflows on a phone
<div className="grid grid-cols-4 gap-6">…</div>

// Good: one column on phones, more as the screen grows
<div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">…</div>

// Wide tables scroll inside their container, not the page
<div className="overflow-x-auto">
    <table className="min-w-full text-sm">…</table>
</div>
```

## Progressive disclosure

```tsx
const [open, setOpen] = useState(false);

<Button variant="link" aria-expanded={open} aria-controls="advanced-settings" onClick={() => setOpen(!open)}>
    {t('settings.advanced')}
</Button>
{open ? <div id="advanced-settings">…</div> : null}
```

## Status announcement

```tsx
// A copy or save confirmation that screen readers hear without moving focus
<p role="status" aria-live="polite" className="text-sm text-muted-foreground">
    {copied ? t('common.copied') : ''}
</p>
```

Keep the live region mounted and change its text. A region that mounts together with its message is often not announced.

## Tests that double as an accessibility check

```tsx
// Bad: couples to markup and passes even when the control has no name
container.querySelector('.delete-btn')!.click();

// Good: fails if the role or accessible name is missing
await user.click(screen.getByRole('button', { name: 'Team löschen' }));
expect(screen.getByLabelText('E-Mail')).toHaveAttribute('aria-invalid', 'true');
```
