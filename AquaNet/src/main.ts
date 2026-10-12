import { mount } from 'svelte';
import './app.sass'
import App from './App.svelte'
import { addCollection } from '@iconify/svelte'
import icons from './libs/icons.generated.json'

// Icons are bundled at build time (tools/bundle-icons.js) so they work without internet
icons.forEach(c => addCollection(c as any))

const app = mount(App, { target: document.getElementById("app")! });

export default app
