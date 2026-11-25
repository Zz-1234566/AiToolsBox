# sr-file-choose

## 功能
本地文件选择，例如.txt文件、.pdf文件等
uniapp编译到App时，选择文件的方法不够全面，只支持图片和视频，想要选择本地其他格式的文件，目前需要自己去实现。

通过uts插件，实现了uniapp编译到安卓、苹果和鸿蒙App之后的文件选择功能。


## 使用方法

1. 通过插件市场下载并导入项目，在uni_modules 目录下
```javascript
  import { chooseFileFromModule } from '../../uni_modules/sr-file-choose'
```

2. 在需要的地方直接调用`chooseFileFromModule`方法，示例中只用了`complete`完成回调，如何需要区分成功失败，可以自行实现`success`和`fail`回调。
```javascript
  chooseFileFromModule({
	complete: (res) => {
		console.log(res)
	    let path = res.path							   
	    let name = res.name
	    let fileType = ''
	   // 如果没有name，默认为：截取最后一个/之后的内容
	   if(!name) {
		   let lastIndex = path.lastIndexOf('/');
		   if (lastIndex !== -1) {
			   name = path.substring(lastIndex + 1);
		   } else {
			   name = Math.random().toString(36).substr(2) + Date.now();
		   }
	   }
	   // 使用 lastIndexOf 方法找到最后一个 . 的位置
	   let lastDotIndex = name.lastIndexOf('.');
	   if (lastDotIndex !== -1) {
		   fileType = name.substring(lastDotIndex + 1);
	   } else {
		   console.log('文件路径中没有 .');
	   }
	   let file = {
		   size: res.size,
		   path,
		   fileType,
		   name
	   }
	   console.log('根据需求构造的数据', file)
	}
})
}
```

