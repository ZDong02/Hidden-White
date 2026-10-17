import js from "@eslint/js";
import globals from "globals";
import pluginVue from "eslint-plugin-vue";
import { defineConfig } from "eslint/config";

// uni-app / 微信小程序运行时全局变量
const uniappGlobals = {
  uni: "readonly",
  wx: "readonly",
  plus: "readonly",
  getCurrentPages: "readonly",
  getApp: "readonly",
  App: "readonly",
  Page: "readonly",
  Component: "readonly",
  Behavior: "readonly",
  requirePlugin: "readonly",
};

export default defineConfig([
  {
    ignores: ["node_modules/**", "dist/**", "unpackage/**", "preview/**"],
  },
  {
    files: ["**/*.{js,mjs,cjs,vue}"],
    plugins: { js },
    extends: ["js/recommended"],
    languageOptions: { globals: { ...globals.browser, ...globals.node, ...uniappGlobals } },
  },
  pluginVue.configs["flat/essential"],
  {
    // uni-app 页面文件按 pages.json 约定单词命名（index.vue、order.vue 等）
    files: ["pages/**/*.vue"],
    rules: { "vue/multi-word-component-names": "off" },
  },
  {
    // components/ui/ 是项目自有基础组件库，命名沿用通用词汇（Button / Modal /
    // Input / Card / Stepper），与 Element Plus 等主流库的用法一致。
    // 这些名字已被全项目 import，重命名收益低于成本，故豁免该规则。
    files: ["components/ui/**/*.vue"],
    rules: { "vue/multi-word-component-names": "off" },
  },
]);
