<script lang="ts">
  let {
    letters,
    onSelect,
  }: {
    letters: string[];
    onSelect: (letter: string) => void;
  } = $props();
</script>

{#if letters.length > 0}
  <div
    class="alphabet-index"
    style="position: fixed; right: 4px; top: 140px; bottom: 20px; display: flex; flex-direction: column; justify-content: space-around; align-items: center; z-index: 10; user-select: none; font-size: 11px; font-weight: 600; color: #2196f3; padding: 4px 2px; background: rgba(0,0,0,0.3); border-radius: 10px; max-height: calc(100vh - 160px);"
  >
    {#each letters as letter}
      <button
        onclick={() => onSelect(letter)}
        style="background: none; border: none; color: inherit; font: inherit; padding: 1px 4px; cursor: pointer; line-height: 1;"
      >
        {letter}
      </button>
    {/each}
  </div>
{/if}
