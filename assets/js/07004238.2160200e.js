"use strict";(self.webpackChunkdocs=self.webpackChunkdocs||[]).push([[7950],{3905:function(e,r,n){n.d(r,{Zo:function(){return l},kt:function(){return g}});var t=n(7294);function o(e,r,n){return r in e?Object.defineProperty(e,r,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[r]=n,e}function a(e,r){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var t=Object.getOwnPropertySymbols(e);r&&(t=t.filter((function(r){return Object.getOwnPropertyDescriptor(e,r).enumerable}))),n.push.apply(n,t)}return n}function s(e){for(var r=1;r<arguments.length;r++){var n=null!=arguments[r]?arguments[r]:{};r%2?a(Object(n),!0).forEach((function(r){o(e,r,n[r])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):a(Object(n)).forEach((function(r){Object.defineProperty(e,r,Object.getOwnPropertyDescriptor(n,r))}))}return e}function i(e,r){if(null==e)return{};var n,t,o=function(e,r){if(null==e)return{};var n,t,o={},a=Object.keys(e);for(t=0;t<a.length;t++)n=a[t],r.indexOf(n)>=0||(o[n]=e[n]);return o}(e,r);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);for(t=0;t<a.length;t++)n=a[t],r.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(o[n]=e[n])}return o}var p=t.createContext({}),c=function(e){var r=t.useContext(p),n=r;return e&&(n="function"==typeof e?e(r):s(s({},r),e)),n},l=function(e){var r=c(e.components);return t.createElement(p.Provider,{value:r},e.children)},u={inlineCode:"code",wrapper:function(e){var r=e.children;return t.createElement(t.Fragment,{},r)}},d=t.forwardRef((function(e,r){var n=e.components,o=e.mdxType,a=e.originalType,p=e.parentName,l=i(e,["components","mdxType","originalType","parentName"]),d=c(n),g=o,h=d["".concat(p,".").concat(g)]||d[g]||u[g]||a;return n?t.createElement(h,s(s({ref:r},l),{},{components:n})):t.createElement(h,s({ref:r},l))}));function g(e,r){var n=arguments,o=r&&r.mdxType;if("string"==typeof e||o){var a=n.length,s=new Array(a);s[0]=d;var i={};for(var p in r)hasOwnProperty.call(r,p)&&(i[p]=r[p]);i.originalType=e,i.mdxType="string"==typeof e?e:o,s[1]=i;for(var c=2;c<a;c++)s[c]=n[c];return t.createElement.apply(null,s)}return t.createElement.apply(null,n)}d.displayName="MDXCreateElement"},3136:function(e,r,n){n.r(r),n.d(r,{assets:function(){return l},contentTitle:function(){return p},default:function(){return g},frontMatter:function(){return i},metadata:function(){return c},toc:function(){return u}});var t=n(7462),o=n(3366),a=(n(7294),n(3905)),s=["components"],i={sidebar_position:2},p="Saving Progress",c={unversionedId:"guides/saving-progress",id:"version-4.0/guides/saving-progress",title:"Saving Progress",description:"A common use-case is to store the users progress on a particular Track",source:"@site/versioned_docs/version-4.0/guides/saving-progress.md",sourceDirName:"guides",slug:"/guides/saving-progress",permalink:"/docs/4.0/guides/saving-progress",editUrl:"https://github.com/doublesymmetry/react-native-track-player/tree/main/docs/versioned_docs/version-4.0/guides/saving-progress.md",tags:[],version:"4.0",sidebarPosition:2,frontMatter:{sidebar_position:2},sidebar:"app",previous:{title:"Offline Playback",permalink:"/docs/4.0/guides/offline-playback"},next:{title:"Sleeptimers",permalink:"/docs/4.0/guides/sleeptimers"}},l={},u=[{value:"Naive Approach",id:"naive-approach",level:2},{value:"Recommended Approach",id:"recommended-approach",level:2}],d={toc:u};function g(e){var r=e.components,n=(0,o.Z)(e,s);return(0,a.kt)("wrapper",(0,t.Z)({},d,n,{components:r,mdxType:"MDXLayout"}),(0,a.kt)("h1",{id:"saving-progress"},"Saving Progress"),(0,a.kt)("p",null,"A common use-case is to store the users progress on a particular ",(0,a.kt)("inlineCode",{parentName:"p"},"Track"),"\nsomewhere so that when they leave and come back, they can pick up right where\nthey left off. To do this you need to listen for progress updates and then\nstore the progress somewhere. There are two high level ways of getting this\ndone."),(0,a.kt)("h2",{id:"naive-approach"},"Naive Approach"),(0,a.kt)("p",null,"One approach could be to use the progress events/updates that the ",(0,a.kt)("inlineCode",{parentName:"p"},"useProgress"),"\nhook provides. This isn't a very good idea and here's why:"),(0,a.kt)("p",null,'Users can listen to audio both "in-App" and "Remotely". In-App would be defined\nas playback while the user has the app opened on screen. However, whenever\naudio is being played in the background/remotely. For example: playback on the\nlockscreen, carplay, etc. In these situations ',(0,a.kt)("strong",{parentName:"p"},"the UI is not mounted"),", meaning\nthe ",(0,a.kt)("inlineCode",{parentName:"p"},"useProgress")," hook, or really any event listeners that are registered\ninside of your App UI tree (anything called as a result of\n",(0,a.kt)("inlineCode",{parentName:"p"},"AppRegistry.registerComponent(appName, () => App);")," in your ",(0,a.kt)("inlineCode",{parentName:"p"},"index.js")," file)\n",(0,a.kt)("strong",{parentName:"p"},"WILL NOT EXECUTE"),"."),(0,a.kt)("p",null,"In a nutshell, if you do this, you're progress ",(0,a.kt)("strong",{parentName:"p"},"will not")," update when the user\nis playing back in Remote contexts and therefore your app will seem buggy."),(0,a.kt)("h2",{id:"recommended-approach"},"Recommended Approach"),(0,a.kt)("p",null,"The correct way to handle this is to track progress in the\n",(0,a.kt)("a",{parentName:"p",href:"/docs/4.0/basics/playback-service"},"Playback Service"),", based on the\n",(0,a.kt)("inlineCode",{parentName:"p"},"Event.PlaybackProgressUpdated")," event. These events fire all the time, including\nwhen your app is playing back remotely."))}g.isMDXComponent=!0}}]);