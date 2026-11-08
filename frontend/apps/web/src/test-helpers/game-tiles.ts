import { within } from '@testing-library/react';

/**
 * The game tiles of a games list (see GameResults), each as lines of announced
 * text: the title, one line per team row, then the notes when present. Hidden
 * decoration (aria-hidden, e.g. the trophy or a dash) is left out, so a line
 * reads as a screen reader would, e.g. "Alpha, Gewinner 60 +2 Siegpunkte".
 */
export function gameTiles(list: HTMLElement): string[][] {
    return directItems(list).map((tile) =>
        Array.from(tile.children).flatMap((part) =>
            part.tagName === 'UL' ? directItems(part).map(announced) : [announced(part)],
        ),
    );
}

/** A list's items as announced text, e.g. the Victory Point standings. */
export function listItems(list: HTMLElement): string[] {
    return directItems(list).map(announced);
}

function directItems(list: Element): HTMLElement[] {
    return within(list as HTMLElement)
        .getAllByRole('listitem')
        .filter((item) => item.parentElement === list);
}

function announced(element: Element): string {
    const parts: string[] = [];
    const walk = (node: Node) => {
        if (node instanceof Element && node.getAttribute('aria-hidden') === 'true') return;
        if (node.nodeType === Node.TEXT_NODE) parts.push(node.textContent ?? '');
        node.childNodes.forEach(walk);
    };
    walk(element);
    return parts.join(' ').replace(/\s+/g, ' ').replace(/ ,/g, ',').trim();
}
