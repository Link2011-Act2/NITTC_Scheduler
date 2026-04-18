# NITTC Scheduler



鶴岡高専用時間割・課題管理アプリ(Android)



## 概要

鶴岡高専では、普通の時間割に加え、A授業日とB授業日が決まっており、A/Bを確認しないと今日はなにの授業があるのかがわかりません

なので、A/B日の表と時間割を入力することで、当日の時間割を出力するアプリを開発しました

~~NITTCは8校あるとか言わないでください リリースしてから気づきました~~



### 機能

* 時間割管理 (教科・担当教員・授業場所)
* 時間割と紐づけた課題管理
* 時間割様式の設定機能 (コマ数や授業時間等を設定できるため、高専だけでなく一般校でも使用可能です)

### 実験的機能

* ローカルAIを使用して、画像から時間割を自動インポート (Gemma4, SmolVLM2, Qwen3.5が使用可能)
* カレンダーへ課題期日・授業時間を追加



## ローカルAIについて

このアプリはローカルAIを使うことができますが、ローカルAIはCPUリソース・RAMを大量に使用するため、使う際は使用するスマホのスペックをよく確認して使用してください

最低動作スペックは**RAM8GB以上・Snapdragon 7s Gen 3以上**、推奨スペックは**RAM12GB以上・Snapdragon 8+ Gen 1以上**です



また、モデルによってはダウンロードするためにHugging Faceのアクセストークン(ReadのみでOK)が必要です

なお、このアクセストークンはAIモデルをダウンロードするためだけに使用し、それ以外の用途には使用しません。



## ライセンス

MIT Licenseです

好きにいじくってもらって構いません



## 動作確認済みの機種

* Nothing Phone (2) Pong-B4.0-260226-0955
* Nothing Phone (3) Metroid-B4.0-260206-1135-JPN

## 動作イメージ
<img width="300" alt="HGLdI_la0AAvCw8" src="https://github.com/user-attachments/assets/adbde189-2862-4e6a-87ad-ab11743a8b12" />
<img width="300" alt="HGLdJEXbYAEnZYY" src="https://github.com/user-attachments/assets/971ccfcc-b59d-48e5-895b-2d4da1778b91" />
<img width="300" alt="HGLdJHIa4AAood3" src="https://github.com/user-attachments/assets/79d464d3-b98d-482c-86f0-51db5f25ee1d" />
<img width="300" alt="HGLdJEraEAAY6SM" src="https://github.com/user-attachments/assets/5353da59-663c-4d23-9372-4bf0f1cd7417" />





