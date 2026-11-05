<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getRecommended, getHomeArticles, getStats } from '@/api/home'
import ProductCard from '@/components/ProductCard.vue'
import RequestState from '@/components/RequestState.vue'
const router = useRouter()
const search = ref('')
const recommended = ref([])
const articles = ref([])
const stats = ref(null)
const state = reactive({ products: 'loading', articles: 'loading', stats: 'loading' })
async function loadProducts() {
  state.products = 'loading'
  try { recommended.value = await getRecommended() || []; state.products = 'ready' }
  catch { state.products = 'error' }
}
async function loadArticles() {
  state.articles = 'loading'
  try { articles.value = await getHomeArticles() || []; state.articles = 'ready' }
  catch { state.articles = 'error' }
}
async function loadStats() {
  state.stats = 'loading'
  try { stats.value = await getStats(); state.stats = 'ready' }
  catch { state.stats = 'error' }
}
function findProducts() { router.push({ path: '/products', query: search.value.trim() ? { keyword: search.value.trim() } : {} }) }
onMounted(() => { loadProducts(); loadArticles(); loadStats() })
</script>
<template>
  <div class="home">
    <section class="home-hero">
      <div class="hero-copy container"><span class="hero-eyebrow">农产品交易 · 联保融资 · 专家助力</span><h1>田间的好物，<br />生活的好滋味</h1><p>在融销通，发现农产品，寻找农业资金支持，<br class="desktop-break" />让专业知识陪伴每一次耕耘。</p>
        <form class="hero-search" @submit.prevent="findProducts"><el-icon :size="20"><Search /></el-icon><input v-model="search" aria-label="搜索农产品" placeholder="试试大米、苹果、新鲜蔬菜" /><button type="submit" class="button-link">发现好物<el-icon><ArrowRight /></el-icon></button></form>
        <div class="hero-keywords"><span>从一份好食材开始</span><router-link to="/products?category=GRAIN">粮油</router-link><router-link to="/products?category=FRUIT">水果</router-link><router-link to="/products?category=VEG">蔬菜</router-link></div>
      </div>
      <div class="hero-photograph"><img src="/images/longsheng-terraces.jpg" alt="广西龙胜梯田，层叠田野间的农业风景" fetchpriority="high" /><div class="hero-photo-caption"><span>广西 · 龙胜梯田</span><span>耕耘，让土地长出可能。</span></div></div>
    </section>
    <section class="container service-paths" aria-label="农业服务入口">
      <router-link to="/products" class="service-path"><el-icon><Goods /></el-icon><div><h2>把好收成带回家</h2><p>发现农产品与背后的农户</p></div><el-icon class="path-arrow"><TopRight /></el-icon></router-link>
      <router-link to="/financing/groups" class="service-path"><el-icon><Connection /></el-icon><div><h2>为下一季添份支持</h2><p>了解联保小组与融资申请</p></div><el-icon class="path-arrow"><TopRight /></el-icon></router-link>
      <router-link to="/experts" class="service-path"><el-icon><ChatDotRound /></el-icon><div><h2>让专业走进田间</h2><p>向农业专家提问与预约</p></div><el-icon class="path-arrow"><TopRight /></el-icon></router-link>
    </section>
    <section class="container home-section"><div class="section-heading"><div><h2>好物，值得被看见</h2><p>从粮油到果蔬，挑选你需要的农产品。</p></div><router-link to="/products" class="text-link">逛逛农产品集市<el-icon><ArrowRight /></el-icon></router-link></div>
      <RequestState :loading="state.products === 'loading'" :error="state.products === 'error'" :empty="state.products === 'ready' && !recommended.length" empty-text="暂无在售农产品，稍后再来看看" @retry="loadProducts"><div class="product-grid"><ProductCard v-for="p in recommended.slice(0, 8)" :key="p.id" :product="p" /></div></RequestState>
    </section>
    <section class="container knowledge-section"><div class="knowledge-intro"><h2>让经验，<br />助力每季丰收。</h2><p>读一篇农业知识，<br />为田间的每个问题多找一个答案。</p><router-link to="/articles" class="text-link">浏览农业知识<el-icon><ArrowRight /></el-icon></router-link></div><div class="knowledge-list"><RequestState :loading="state.articles === 'loading'" :error="state.articles === 'error'" :empty="state.articles === 'ready' && !articles.length" empty-text="农业知识正在整理中" @retry="loadArticles"><router-link v-for="a in articles" :key="a.id" :to="`/articles/${a.id}`" class="knowledge-row"><div><span class="knowledge-category">农业知识</span><h3>{{ a.title }}</h3><span class="muted">{{ a.viewCount ?? 0 }} 次阅读</span></div><el-icon><TopRight /></el-icon></router-link></RequestState></div></section>
    <section class="container platform-record"><div><h2>在这里，农业相互连接</h2><p>{{ stats?.demoMode ? '当前为课程演示数据环境，以下为库内实时统计。' : '以下数据来自平台当前记录。' }}</p></div><div v-if="state.stats === 'ready'" class="platform-totals"><div><strong>{{ stats.productCount }}</strong><span>在售农产品</span></div><div><strong>{{ stats.financingSuccess }}</strong><span>已通过联保小组</span></div><div><strong>{{ stats.userCount }}</strong><span>注册账户</span></div></div><el-button v-else-if="state.stats === 'error'" @click="loadStats">统计加载失败，重试</el-button><span v-else class="muted" role="status">正在读取平台记录…</span></section>
  </div>
</template>
