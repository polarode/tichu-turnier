import {
    columnVisibilityFeature,
    createColumnHelper,
    createSortedRowModel,
    FlexRender,
    rowSortingFeature,
    type RowData,
    tableFeatures,
    useTable,
} from '@tanstack/react-table';
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import type { ComponentProps, ReactNode } from 'react';
import { useMemo } from 'react';

import { cn } from './lib/utils.ts';
import {
    Table,
    TableBody,
    TableCaption,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from './table.tsx';

export interface DataTableColumn<Row> {
    id: string;
    /** Visible header content (may be an <abbr>). */
    header: ReactNode;
    cell: (row: Row) => ReactNode;
    /** Present = sortable. null means "no value" and always sorts last, both directions. */
    sortValue?: (row: Row) => number | string | null;
    /** First direction a click applies; default 'desc'. Text and rank columns pass 'asc'. */
    sortFirst?: 'asc' | 'desc';
    /** Render this column's body cells as <th scope="row"> (the row's name). */
    rowHeader?: boolean;
    align?: 'start' | 'end';
    headClassName?: string;
    cellClassName?: string;
}

export interface DataTableProps<Row extends RowData> {
    columns: readonly DataTableColumn<Row>[];
    rows: readonly Row[];
    getRowId: (row: Row) => string;
    /** Rendered as an sr-only caption. */
    caption: string;
    /** Ids of the columns to show; all when omitted. */
    visibleColumns?: readonly string[];
    /** Extra props per body row (e.g. aria-current, className). */
    rowProps?: (row: Row) => ComponentProps<'tr'>;
    /** Locale for comparing text sort values (Intl.Collator). */
    locale?: string;
    className?: string;
}

const features = tableFeatures({
    columnVisibilityFeature,
    rowSortingFeature,
    sortedRowModel: createSortedRowModel(),
});

type SortableValue = number | string | undefined;

/** What an active sort direction shows: its aria-sort value and its arrow. */
const SORT_DIRECTIONS = {
    asc: { ariaSort: 'ascending', Icon: ArrowUp },
    desc: { ariaSort: 'descending', Icon: ArrowDown },
} as const;

/** The compact, wrapping look every column starts from; columns add to it. */
const HEAD_CLASS = 'h-auto px-3 py-2 font-medium whitespace-normal text-muted-foreground';
const CELL_CLASS = 'px-3 py-2 whitespace-normal text-inherit';

function compareSortValues(a: SortableValue, b: SortableValue, collator: Intl.Collator): number {
    if (typeof a === 'number' && typeof b === 'number') {
        return a - b;
    }
    return collator.compare(String(a), String(b));
}

export function DataTable<Row extends RowData>({
    columns,
    rows,
    getRowId,
    caption,
    visibleColumns,
    rowProps,
    locale,
    className,
}: DataTableProps<Row>) {
    // Rows don't tint on hover: a sticky cell keeps its own background and would look split.
    const rowPropsOf = (row: Row) => {
        const props = rowProps?.(row);
        return { ...props, className: cn('hover:bg-transparent', props?.className) };
    };
    const collator = useMemo(() => new Intl.Collator(locale), [locale]);
    const helper = createColumnHelper<typeof features, Row>();
    const defs = columns.map((column) =>
        helper.accessor((row) => column.sortValue?.(row) ?? undefined, {
            id: column.id,
            header: () => column.header,
            cell: (context) => column.cell(context.row.original),
            enableSorting: column.sortValue !== undefined,
            sortDescFirst: (column.sortFirst ?? 'desc') === 'desc',
            sortUndefined: 'last',
            sortFn: (rowA, rowB, columnId) =>
                compareSortValues(
                    rowA.getValue<SortableValue>(columnId),
                    rowB.getValue<SortableValue>(columnId),
                    collator,
                ),
        }),
    );
    const table = useTable({
        features,
        columns: helper.columns(defs),
        data: rows as Row[],
        getRowId,
        state: {
            columnVisibility: visibleColumns
                ? Object.fromEntries(
                      columns.map((column) => [column.id, visibleColumns.includes(column.id)]),
                  )
                : {},
        },
    });

    return (
        <Table className={className}>
            <TableCaption className="sr-only">{caption}</TableCaption>
            <TableHeader>
                {table.getHeaderGroups().map((headerGroup) => (
                    <TableRow key={headerGroup.id}>
                        {headerGroup.headers.map((header) => {
                            const column = columns.find((c) => c.id === header.column.id);
                            const alignEnd = column?.align === 'end';
                            const sorted = header.column.getIsSorted();
                            const direction = sorted ? SORT_DIRECTIONS[sorted] : undefined;
                            return (
                                <TableHead
                                    key={header.id}
                                    scope="col"
                                    aria-sort={direction?.ariaSort}
                                    className={cn(
                                        HEAD_CLASS,
                                        alignEnd && 'text-right',
                                        column?.headClassName,
                                    )}
                                >
                                    {header.column.getCanSort() ? (
                                        <button
                                            type="button"
                                            onClick={() => header.column.toggleSorting()}
                                            className={cn(
                                                'inline-flex items-center gap-1 rounded-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-offset-1 focus-visible:ring-offset-background',
                                                alignEnd && 'w-full justify-end',
                                            )}
                                        >
                                            {column?.header}
                                            {direction ? (
                                                <direction.Icon
                                                    aria-hidden="true"
                                                    className="size-4"
                                                />
                                            ) : (
                                                <ArrowUpDown
                                                    aria-hidden="true"
                                                    className="size-4 opacity-40"
                                                />
                                            )}
                                        </button>
                                    ) : (
                                        column?.header
                                    )}
                                </TableHead>
                            );
                        })}
                    </TableRow>
                ))}
            </TableHeader>
            <TableBody>
                {table.getRowModel().rows.map((row) => (
                    <TableRow key={row.id} {...rowPropsOf(row.original)}>
                        {row.getVisibleCells().map((cell) => {
                            const column = columns.find((c) => c.id === cell.column.id);
                            const className = cn(
                                CELL_CLASS,
                                column?.align === 'end' && 'text-right',
                                column?.cellClassName,
                            );
                            if (column?.rowHeader) {
                                return (
                                    <TableHead key={cell.id} scope="row" className={className}>
                                        <FlexRender cell={cell} />
                                    </TableHead>
                                );
                            }
                            return (
                                <TableCell key={cell.id} className={className}>
                                    <FlexRender cell={cell} />
                                </TableCell>
                            );
                        })}
                    </TableRow>
                ))}
            </TableBody>
        </Table>
    );
}
