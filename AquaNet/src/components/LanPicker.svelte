<script lang="ts">
  import { slide } from "svelte/transition";
  import Icon from "@iconify/svelte";
  import { LAN, type LanPlayer } from "../libs/sdk";

  // "Who's playing?" for LAN mode: tap your name, or type the access code printed on your card
  let players: LanPlayer[] | null = null
  let error = ""
  let accessCode = ""
  let busy: number | "code" | null = null

  const gameNames: Record<string, string> = {
    chu3: "CHUNITHM", mai2: "maimai DX", ongeki: "O.N.G.E.K.I.", wacca: "WACCA",
  }

  LAN.players().then(p => players = p).catch(e => error = e.message)

  async function login(by: { cardId: number } | { accessCode: string }) {
    busy = "cardId" in by ? by.cardId : "code"
    error = ""
    try {
      const { username } = await LAN.login(by)
      window.location.href = `/u/${username}`
    } catch (e: any) {
      error = e.message
      busy = null
    }
  }
</script>

<div class="lan" transition:slide>
  <h2>Who's playing?</h2>

  {#if error}
    <span class="error">{error}</span>
  {/if}

  {#if players === null && !error}
    <Icon icon="line-md:loading-twotone-loop"/>
  {:else if players && players.length === 0}
    <span class="hint">Nobody has played yet. Scan a card on a cabinet, then come back.</span>
  {:else if players}
    <div class="players">
      {#each players as p (p.cardId)}
        <button on:click={() => login({ cardId: p.cardId })} disabled={busy !== null}>
          <span class="name">{p.name || p.username}</span>
          <span class="games">{p.games.map(g => gameNames[g] ?? g).join(" · ")}</span>
          {#if busy === p.cardId}<Icon icon="line-md:loading-twotone-loop"/>{/if}
        </button>
      {/each}
    </div>
  {/if}

  <form class="code" on:submit|preventDefault={() => accessCode.trim() && login({ accessCode })}>
    <input type="text" inputmode="numeric" placeholder="Or type a card's 20-digit access code" bind:value={accessCode}>
    <button type="submit" disabled={busy !== null || !accessCode.trim()}>
      {#if busy === "code"}<Icon icon="line-md:loading-twotone-loop"/>{:else}Go{/if}
    </button>
  </form>
</div>

<style lang="sass">
  @use "../vars"

  .lan
    display: flex
    flex-direction: column
    gap: 12px
    width: min(560px, calc(100vw - 32px))

  h2
    margin: 0

  .hint
    opacity: 0.7

  .error
    color: vars.$c-error

  .players
    display: grid
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr))
    gap: 8px

    button
      display: flex
      flex-direction: column
      align-items: flex-start
      gap: 4px
      text-align: left

      .name
        font-size: 1.15em

      .games
        font-size: 0.8em
        opacity: 0.6

  .code
    display: flex
    gap: 8px

    input
      flex: 1
      min-width: 0
</style>
