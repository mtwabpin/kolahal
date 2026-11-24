/**
 * Where the scroll tour (components/Tour.astro) is shown. It always has a page of its own, /tour/.
 *
 * - Off (the default): the home page doesn't show it, and the hero's "Take the tour" opens /tour/.
 * - On: the home page shows it too, after "Scripts and widgets", and "Take the tour" goes down to it.
 *
 * To preview the other state without changing this file: `PUBLIC_TOUR_ON_HOME=true pnpm build` (or `=false`).
 */
const TOUR_ON_HOME_ON = false;

const override = import.meta.env.PUBLIC_TOUR_ON_HOME;
export const TOUR_ON_HOME: boolean = override === 'true' ? true : override === 'false' ? false : TOUR_ON_HOME_ON;
