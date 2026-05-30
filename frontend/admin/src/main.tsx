// AntD v5 的 message、modal、notification 在 React 19 下依赖该补丁完成动态挂载。
import '@ant-design/v5-patch-for-react-19';
import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import App from './App.tsx';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
