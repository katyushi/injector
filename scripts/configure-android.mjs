import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const projectDir = path.resolve(scriptDir, '..');
const androidDir = path.join(projectDir, 'android');
const manifestPath = path.join(androidDir, 'app', 'src', 'main', 'AndroidManifest.xml');
const gradlePath = path.join(androidDir, 'app', 'build.gradle');
const sourceJava = path.join(projectDir, 'native', 'android', 'java', 'com', 'figurinhas', 'injector');
const targetJava = path.join(androidDir, 'app', 'src', 'main', 'java', 'com', 'figurinhas', 'injector');

if (!fs.existsSync(manifestPath) || !fs.existsSync(gradlePath)) {
  throw new Error('Android ainda não foi criado. Rode primeiro: pnpm cap:add:android');
}

fs.mkdirSync(targetJava, { recursive: true });
fs.cpSync(sourceJava, targetJava, { recursive: true, force: true });
const kotlinMainActivity = path.join(targetJava, 'MainActivity.kt');
if (fs.existsSync(kotlinMainActivity)) fs.rmSync(kotlinMainActivity);

let manifest = fs.readFileSync(manifestPath, 'utf8');
if (!manifest.includes('StickerContentProvider')) {
  const provider = `
        <provider
            android:name=".StickerContentProvider"
            android:authorities="\${applicationId}.stickers"
            android:enabled="true"
            android:exported="true"
            android:readPermission="com.whatsapp.sticker.READ" />`;
  manifest = manifest.replace('</application>', `${provider}\n    </application>`);
}
if (!manifest.includes('android:name="com.whatsapp"')) {
  const queries = `
    <queries>
        <package android:name="com.whatsapp" />
        <package android:name="com.whatsapp.w4b" />
    </queries>`;
  manifest = manifest.replace('</manifest>', `${queries}\n</manifest>`);
}
fs.writeFileSync(manifestPath, manifest, 'utf8');

let gradle = fs.readFileSync(gradlePath, 'utf8');
if (!gradle.includes('androidx.documentfile:documentfile')) {
  gradle = gradle.replace(/dependencies\s*\{/, (block) => `${block}\n    implementation 'androidx.documentfile:documentfile:1.0.1'`);
  fs.writeFileSync(gradlePath, gradle, 'utf8');
}

console.log('Sticker Injector Android bridge configurada.');
