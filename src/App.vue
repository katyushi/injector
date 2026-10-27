<script setup>
import { computed, onMounted, ref } from 'vue';
import { Capacitor, registerPlugin } from '@capacitor/core';

const StickerBridge = registerPlugin('StickerBridge');
const packs = ref([]);
const busy = ref(false);
const message = ref('');
const messageKind = ref('');
const nativeAndroid = computed(() => Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android');

function notify(text, kind = 'info') {
  message.value = text;
  messageKind.value = kind;
}

async function refreshPacks() {
  if (!nativeAndroid.value) return;
  try {
    const result = await StickerBridge.listPacks();
    packs.value = result.packs || [];
  } catch (error) {
    notify(error.message || 'Não consegui carregar os packs importados.', 'error');
  }
}

async function importCollection() {
  if (!nativeAndroid.value) return;
  busy.value = true;
  notify('Selecione uma collection gerada pelo Figurinhas.', 'info');
  try {
    const result = await StickerBridge.importCollection();
    await refreshPacks();
    notify(`${result.imported} pack(s) importado(s). Agora você pode adicionar cada um ao WhatsApp.`, 'success');
  } catch (error) {
    if (error.code !== 'PICKER_CANCELLED') notify(error.message || 'Não consegui importar essa collection.', 'error');
    else notify('Importação cancelada.', 'info');
  } finally {
    busy.value = false;
  }
}

async function addPack(pack) {
  busy.value = true;
  notify(`Abrindo a confirmação do WhatsApp para “${pack.name}”…`, 'info');
  try {
    await StickerBridge.addPack({ identifier: pack.identifier });
    notify('O WhatsApp abriu. Confirme a adição do pack no próprio WhatsApp.', 'success');
  } catch (error) {
    notify(error.message || 'Não consegui abrir a confirmação do WhatsApp.', 'error');
  } finally {
    busy.value = false;
  }
}

async function removePack(pack) {
  const confirmed = window.confirm(`Remover “${pack.name}” do injector? Se ele já foi adicionado ao WhatsApp, remova-o do WhatsApp antes de apagar a cópia local.`);
  if (!confirmed) return;
  busy.value = true;
  try {
    await StickerBridge.removePack({ identifier: pack.identifier });
    await refreshPacks();
    notify(`“${pack.name}” foi removido do injector.`, 'success');
  } catch (error) {
    notify(error.message || 'Não consegui remover esse pack.', 'error');
  } finally {
    busy.value = false;
  }
}

onMounted(refreshPacks);
</script>

<template>
  <main class="page-shell">
    <header class="topbar">
      <a class="brand" href="#" aria-label="Figurinhas Injector">
        <span class="brand-mark" aria-hidden="true">✳</span>
        <span>figurinhas<span class="brand-light"> / injector</span></span>
      </a>
      <span class="local-status"><i></i> Seus packs ficam no dispositivo</span>
    </header>

    <section class="hero">
      <p class="eyebrow">Importar · organizar · adicionar</p>
      <h1>Seus stickers,<br /><em>no WhatsApp.</em></h1>
      <p class="intro">Traga uma collection criada pelo Figurinhas e adicione cada pack ao WhatsApp com a confirmação oficial.</p>
    </section>

    <section v-if="!nativeAndroid" class="platform-card" role="status">
      <div class="platform-icon">↗</div>
      <div>
        <h2>Abra o injector no Android</h2>
        <p>O navegador não pode registrar um pack no WhatsApp. Este projeto usa Capacitor e uma ponte Android para importar a collection e chamar a tela de confirmação.</p>
      </div>
      <span class="platform-tag">Android</span>
    </section>

    <template v-else>
      <section class="import-card">
        <div class="import-copy">
          <span class="step">PASSO 01</span>
          <h2>Importe uma collection</h2>
          <p>Escolha a pasta <code>collection-01</code> dentro de <code>done/packs</code>. Ela contém o <code>contents.json</code> e os arquivos dos packs.</p>
        </div>
        <button class="import-button" type="button" :disabled="busy" @click="importCollection">
          <span aria-hidden="true">＋</span> {{ busy ? 'Aguarde…' : 'Escolher pasta' }}
        </button>
      </section>

      <div class="list-heading">
        <div>
          <span class="step">PASSO 02</span>
          <h2>Packs neste dispositivo <span class="count">{{ packs.length }}</span></h2>
        </div>
        <span v-if="packs.length" class="limit-note">Até 10 packs no injector</span>
      </div>

      <section v-if="packs.length" class="pack-grid" aria-live="polite">
        <article v-for="pack in packs" :key="pack.identifier" class="pack-card">
          <div class="pack-topline">
            <span class="pack-icon" :class="{ animated: pack.animated }" aria-hidden="true">{{ pack.animated ? '↻' : '✦' }}</span>
            <span class="type-pill" :class="{ animated: pack.animated }">{{ pack.animated ? 'ANIMADO' : 'ESTÁTICO' }}</span>
          </div>
          <h3>{{ pack.name }}</h3>
          <p class="pack-meta">{{ pack.stickerCount }} stickers <span>·</span> {{ pack.publisher }}</p>
          <button class="add-button" type="button" :disabled="busy" @click="addPack(pack)">
            Adicionar ao WhatsApp <span aria-hidden="true">→</span>
          </button>
          <button class="remove-button" type="button" :disabled="busy" @click="removePack(pack)">Remover do injector</button>
        </article>
      </section>

      <section v-else class="empty-state">
        <span class="empty-symbol" aria-hidden="true">▧</span>
        <h3>Nenhum pack importado ainda</h3>
        <p>Selecione uma collection para ver os packs disponíveis aqui.</p>
      </section>
    </template>

    <p v-if="message" class="notice" :class="messageKind" role="status">{{ message }}</p>

    <footer class="footer">
      <span>O WhatsApp sempre pede confirmação antes de adicionar um pack.</span>
      <span>Estáticos e animados continuam separados.</span>
    </footer>
  </main>
</template>
