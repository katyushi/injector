# Figurinhas Injector

App Android em Vue + Capacitor para importar uma `collection-XX` produzida pelo conversor e adicionar os packs ao WhatsApp. Um site no navegador não consegue registrar o `ContentProvider` exigido pelo WhatsApp; por isso, esta parte usa código Android nativo.

## Preparar e abrir no Android Studio

Requisitos: Node.js 20+, Android Studio, Android SDK e um telefone Android com WhatsApp ou WhatsApp Business.

```sh
npm install
npm run build
npm run cap:sync:android
npm run android
```

No Android Studio, conecte um aparelho e execute o app. Se a pasta `android/` foi removida, recrie-a uma vez com `npm run cap:add:android`. A primeira sincronização/build precisa do Android SDK e da dependência `androidx.documentfile`.

## Usar

1. No computador, copie a pasta `done/packs/collection-01` (ou outra collection) para o telefone.
2. Abra o Injector no Android e toque em **Escolher pasta**.
3. Selecione a pasta da collection. O Injector valida e importa seus packs.
4. Toque em **Adicionar ao WhatsApp** no pack desejado e confirme no WhatsApp.

Uma collection comporta até 10 packs. O app também mantém um máximo de 10 packs importados ao mesmo tempo. Cada pack precisa ter de 3 a 30 stickers estáticos ou animados, sem misturar os tipos.

O Injector copia os arquivos para o armazenamento privado do app e publica os dados por um `ContentProvider` com a permissão de leitura usada pelo WhatsApp. Remova um pack do WhatsApp antes de apagar sua cópia local do Injector; o WhatsApp pode deixar de acessar os stickers se a fonte for removida.

## Compatibilidade

Este MVP implementa a integração Android. A integração iOS usa outro fluxo nativo e ainda não está incluída. O modo web mostra a orientação para abrir o aplicativo Android.
