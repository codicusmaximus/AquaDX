<script lang="ts">
    import { t } from "../libs/i18n";
    import { LAN_MODE } from "../libs/config";

    const tabs: Record<string, string> = {
        [t('home.nav.portal')]: `/home`,
        [t('home.nav.link-card')]: `/cards`,
        // Public-server setup and community pages don't apply on a home network
        ...(LAN_MODE ? {} : {
            [t('home.nav.game-setup')]: `/setup`,
            [t('home.nav.support')]: `/support`
        })
    }
</script>

<nav class="tabs">
    {#each Object.entries(tabs) as t, i}
        <a class="clickable"
            href={t[1]}
            class:active={
                new URL(location.href).pathname === t[1]
            }>
            {t[0]}
        </a>
    {/each}
</nav>

<style lang="sass">
  @use "../vars"
  .tabs
    display: flex
    gap: 1rem

    a
      &.active
        color: vars.$c-main
</style>