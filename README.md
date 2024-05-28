

<table>
  <tr>
    <td align="center">
      <img width="500" alt="파이프라인" src="https://github.com/hs-1971347-shinwoochul/newsflow/assets/162528651/9888fb42-3a96-411c-a9a0-bcd0b720080e">
    </td>
    <td>
      <ul>
        <li><b>주요 적용 기술 및 구조:</b></li>
        <li>개발 환경: Microsoft Windows 10, OS, Android</li>
        <li>개발 도구: Colab, Android Studio</li>
        <li>개발 언어: Python, Pycharm, Java, Kotlin</li>
        <li>관련 기술: Deep Learning, OCR, Socket Programming</li>
      </ul>
    </td>
  </tr>
</table>

```
## Requirements
```

import torch
from torch.utils.data import DataLoader, Dataset
from sklearn.model_selection import train_test_split
from transformers import BartForConditionalGeneration, PreTrainedTokenizerFast
import pytorch_lightning as pl
from pytorch_lightning import Trainer
from transformers import AdamW, get_linear_schedule_with_warmup
from pytorch_lightning.callbacks import ModelCheckpoint
from pytorch_lightning.loggers import TensorBoardLogger


import json
from google.colab import drive

# 구글 드라이브 마운트
drive.mount('/content/drive')

# 데이터 로드
with open('/content/drive/My Drive/ColabNotebooks/NewsFlow(torch)/NewsFlow_dataset(5점이상).json', 'r') as file:
    data = json.load(file)
    documents = data["documents"]

# 데이터 분할
train_docs, val_docs = train_test_split(documents, test_size=0.2, random_state=42)

class NewsFlowDataset(Dataset):
    def __init__(self, documents, tokenizer, max_input_length=768, max_target_length=128):
        self.documents = documents
        self.tokenizer = tokenizer
        self.max_input_length = max_input_length
        self.max_target_length = max_target_length

    def __len__(self):
        return len(self.documents)

    def __getitem__(self, idx):
        doc = self.documents[idx]
        title = doc.get('title', '')
        quality_scores = ' '.join([f"{key}:{value}" for key, value in doc.get('document_quality_scores', {}).items()])
        text_paragraphs = doc.get('text', [])
        extractive_indices = doc.get('extractive', [])
        extractive_sentences = [text_paragraphs[i][0]['sentence'] for i in extractive_indices if i is not None and i < len(text_paragraphs) and text_paragraphs[i]]
        extractive_summary = ' '.join(extractive_sentences)
        article = ' '.join([sentence_data['sentence'] for paragraph in text_paragraphs for sentence_data in paragraph])
        summary = doc['abstractive'][0]

        encoder_inputs = self.tokenizer(article, return_tensors="pt", padding='max_length', truncation=True, max_length=self.max_input_length)
        labels = self.tokenizer(summary, return_tensors="pt", padding='max_length', truncation=True, max_length=self.max_target_length)

        return {
            'input_ids': encoder_inputs['input_ids'].squeeze(0),
            'attention_mask': encoder_inputs['attention_mask'].squeeze(0),
            'labels': labels['input_ids'].squeeze(0)
        }


# 데이터 모듈
class NewsFlowDataModule(pl.LightningDataModule):
    def __init__(self, train_docs, val_docs, tokenizer, batch_size=22, max_input_length=768, max_target_length=128):
        super().__init__()
        self.tokenizer = tokenizer
        self.batch_size = batch_size
        self.max_input_length = max_input_length
        self.max_target_length = max_target_length
        self.train_docs = train_docs
        self.val_docs = val_docs

    def setup(self, stage=None):
        self.train_dataset = NewsFlowDataset(self.train_docs, self.tokenizer, self.max_input_length, self.max_target_length)
        self.val_dataset = NewsFlowDataset(self.val_docs, self.tokenizer, self.max_input_length, self.max_target_length)

    def train_dataloader(self):
        return DataLoader(self.train_dataset, batch_size=self.batch_size, shuffle=True)

    def val_dataloader(self):
        return DataLoader(self.val_dataset, batch_size=self.batch_size)

class NewsFlowModel(pl.LightningModule):
    def __init__(self, tokenizer):
        super().__init__()
        self.model = BartForConditionalGeneration.from_pretrained('gogamza/kobart-base-v1')
        self.tokenizer = tokenizer

    def forward(self, input_ids, attention_mask=None, labels=None):
        return self.model(input_ids, attention_mask=attention_mask, labels=labels)

    def generate(self, input_text):
        raw_input_ids = self.tokenizer.encode(input_text)
        input_ids = [self.tokenizer.bos_token_id] + raw_input_ids + [self.tokenizer.eos_token_id]
        summary_ids = self.model.generate(torch.tensor([input_ids]), num_beams=4, max_length=768, eos_token_id=1)
        return self.tokenizer.decode(summary_ids.squeeze().tolist(), skip_special_tokens=True)


    def training_step(self, batch, batch_idx):
        outputs = self.forward(batch['input_ids'], attention_mask=batch['attention_mask'], labels=batch['labels'])
        loss = outputs.loss
        self.log("train_loss", loss)
        return loss

    def validation_step(self, batch, batch_idx):
        outputs = self.forward(batch['input_ids'], attention_mask=batch['attention_mask'], labels=batch['labels'])
        val_loss = outputs.loss
        self.log("val_loss", val_loss)  # 검증 손실 로깅
        return {"val_loss": val_loss}

    def configure_optimizers(self):
        optimizer = AdamW(self.parameters(), lr=4e-5)
        scheduler = get_linear_schedule_with_warmup(
            optimizer, num_warmup_steps=0, num_training_steps=1000  # 필요에 따라 조정
        )
        return [optimizer], [scheduler]



# 모델, 토크나이저 및 데이터 모듈 생성
tokenizer = PreTrainedTokenizerFast.from_pretrained('gogamza/kobart-base-v1')
news_flow_model = NewsFlowModel(tokenizer)
data_module = NewsFlowDataModule(train_docs, val_docs, tokenizer)

logger = TensorBoardLogger("tb_logs", name="my_model")
checkpoint_callback = ModelCheckpoint(
    dirpath='/content/drive/MyDrive/ColabNotebooks/NewsFlow(torch)/checkpoints',
    filename='checkpoint-{epoch:02d}-{val_loss:.2f}',
    save_top_k=-1,  # 모든 체크포인트 저장
    every_n_epochs=1,  # 매 에포크마다 저장
    monitor='val_loss',  # 검증 손실을 기준으로 모니터링
    mode='min',  # 손실 최소화
    verbose=True
)

# Trainer 설정
trainer = Trainer(
    max_epochs=10,
    callbacks=[checkpoint_callback],
    accelerator='gpu',
    devices=1
)

# 모델 훈련 시작
trainer.fit(news_flow_model, data_module)




