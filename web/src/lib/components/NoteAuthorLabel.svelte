<script lang="ts">
  import type { SongNote, AppSettings } from '../model/types';
  import { personColor } from '../logic/songLogic';

  let {
    note,
    settings,
    number,
    isDark = true,
  }: {
    note: SongNote;
    settings: AppSettings | null;
    number?: number;
    isDark?: boolean;
  } = $props();

  let colorObj = $derived(personColor(note.authorId));
  let color = $derived(isDark ? colorObj.dark : colorObj.light);
  let name = $derived(note.authorName.trim() || 'Unbekannt');

  let label = $derived(
    settings?.noteAuthorNumber && number !== undefined
      ? `${name} · ${number}`
      : name
  );

  let useDot = $derived(settings?.noteAuthorDot ?? false);
  let useColor = $derived(settings?.noteAuthorColoredName ?? true);
</script>

<div style="display: inline-flex; align-items: center; gap: 6px; font-weight: 500; font-size: 14px;">
  {#if useDot}
    <span style="width: 8px; height: 8px; border-radius: 50%; background-color: {color}; display: inline-block;"></span>
  {/if}
  <span style="color: {useColor ? color : 'inherit'}; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
    {label}
  </span>
</div>
