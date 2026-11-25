# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0](https://github.com/apify/apify-camunda-integration/releases/tag/v1.0.0) (2026-11-25)

### 🚀 Features

- Implement custom ApifyClient base ([#3](https://github.com/apify/apify-camunda-integration/pull/3)) ([ed4d1dc](https://github.com/apify/apify-camunda-integration/commit/ed4d1dca5e3197c12b713fe5148704b4e5bdc5f3)) by [@ngut37](https://github.com/ngut37)
- Implement run actor and run task ([#5](https://github.com/apify/apify-camunda-integration/pull/5)) ([4b3c81b](https://github.com/apify/apify-camunda-integration/commit/4b3c81b79bd8ce1cec98c33ae0d35ba97ebe967f)) by [@ngut37](https://github.com/ngut37)
- Implement scrape single URL ([#8](https://github.com/apify/apify-camunda-integration/pull/8)) ([74c67d4](https://github.com/apify/apify-camunda-integration/commit/74c67d48bade8e446d8a97e7a763c00a51a31be8)) by [@ngut37](https://github.com/ngut37)
- Implement get-key-value-store-record ([#7](https://github.com/apify/apify-camunda-integration/pull/7)) ([1f0de51](https://github.com/apify/apify-camunda-integration/commit/1f0de514008f4c0ed39dcf1390a018bf3bcba806)) by [@ngut37](https://github.com/ngut37)
- Implemented inbound connector ([#28](https://github.com/apify/apify-camunda-integration/pull/28)) ([74ff4d3](https://github.com/apify/apify-camunda-integration/commit/74ff4d37fed9df53568f7f675436a5d7d34ebb4b)) by [@daveomri](https://github.com/daveomri)
- Add .gitattributes, license.md, security.md, and github actions worflow for releases, update pom.xml and readme for apify camunda connector ([c21eb11](https://github.com/apify/apify-camunda-integration/commit/c21eb1169b69c5f0c54515d86b60b3921d4ae1f1)) by [@daveomri](https://github.com/daveomri)
- Introduce apifyapiexception for improved error handling and update documentation on element templates ([4e8fb77](https://github.com/apify/apify-camunda-integration/commit/4e8fb77b08212ddffbf139a7843d372df41595a6)) by [@daveomri](https://github.com/daveomri)
- Add connectorWebhookUrl property to element templates and update ApifyInboundExecutable to use it for webhook URL configuration ([6aadba0](https://github.com/apify/apify-camunda-integration/commit/6aadba0ab04b3138a3286eed4fbae0b471b61405)) by [@daveomri](https://github.com/daveomri)
- Enhance release workflow with manual trigger and input validation ([93a0de6](https://github.com/apify/apify-camunda-integration/commit/93a0de6a13142d9bfbec1686f119386b7ec88a40)) by [@daveomri](https://github.com/daveomri)
- Add Web Fetch operation and align Apify wording ([#77](https://github.com/apify/apify-camunda-integration/pull/77)) ([73e0fed](https://github.com/apify/apify-camunda-integration/commit/73e0fed815a49fa9e947c31ca6d518401306c6e3)) by [@daveomri](https://github.com/daveomri), closes [#75](https://github.com/apify/apify-camunda-integration/issues/75)

### 🐛 Bug Fixes

- **deps:** Update version.connectors to v8.8.2 ([#14](https://github.com/apify/apify-camunda-integration/pull/14)) ([5f80a14](https://github.com/apify/apify-camunda-integration/commit/5f80a1497482230043dbf611d4bbb9cc504dff17)) by [@renovate[bot]](https://github.com/renovate[bot])
- **deps:** Update dependency org.apache.httpcomponents.core5:httpcore5 to v5.3.6 ([#19](https://github.com/apify/apify-camunda-integration/pull/19)) ([748807f](https://github.com/apify/apify-camunda-integration/commit/748807f88cdb94795fe6f573880a058e0cd25eca)) by [@renovate[bot]](https://github.com/renovate[bot])
- **deps:** Update dependency org.apache.httpcomponents.core5:httpcore5-h2 to v5.3.6 ([#20](https://github.com/apify/apify-camunda-integration/pull/20)) ([7e29421](https://github.com/apify/apify-camunda-integration/commit/7e29421d095ea898b56ba528cedd5691e21e741f)) by [@renovate[bot]](https://github.com/renovate[bot])
- **deps:** Update dependency org.apache.httpcomponents.client5:httpclient5 to v5.5.1 ([#21](https://github.com/apify/apify-camunda-integration/pull/21)) ([0b3cf94](https://github.com/apify/apify-camunda-integration/commit/0b3cf947c9019b7a9431dc3c77025f3e09e96576)) by [@renovate[bot]](https://github.com/renovate[bot])
- **deps:** Update dependency org.apache.httpcomponents.core5:httpcore5-h2 to v5.4 ([#27](https://github.com/apify/apify-camunda-integration/pull/27)) ([7afb553](https://github.com/apify/apify-camunda-integration/commit/7afb553c8fa1297a006a2dec368013a857715e01)) by [@renovate[bot]](https://github.com/renovate[bot])
- **deps:** Update dependency org.apache.httpcomponents.client5:httpclient5 to v5.5.2 ([#31](https://github.com/apify/apify-camunda-integration/pull/31)) ([e47be1e](https://github.com/apify/apify-camunda-integration/commit/e47be1e8404474bae8e14dacce16e73caf793236)) by [@renovate[bot]](https://github.com/renovate[bot])
- **deps:** Update dependency org.apache.httpcomponents.core5:httpcore5 to v5.4 ([#26](https://github.com/apify/apify-camunda-integration/pull/26)) ([0897540](https://github.com/apify/apify-camunda-integration/commit/089754074e6e072e0b99cba01eb6f942d9222b42)) by [@renovate[bot]](https://github.com/renovate[bot])
- **deps:** Update dependency org.apache.httpcomponents.client5:httpclient5 to v5.6 ([#32](https://github.com/apify/apify-camunda-integration/pull/32)) ([2003228](https://github.com/apify/apify-camunda-integration/commit/2003228b52ded6a8061fee5970179f4393d1c804)) by [@renovate[bot]](https://github.com/renovate[bot])
- **deps:** Update dependency org.apache.httpcomponents.core5:httpcore5 to v5.4.1 ([#44](https://github.com/apify/apify-camunda-integration/pull/44)) ([886f2c6](https://github.com/apify/apify-camunda-integration/commit/886f2c62c96baf976bfd9ae3fccbfad3f52af0c5)) by [@renovate[bot]](https://github.com/renovate[bot])
- **deps:** Update dependency org.apache.httpcomponents.core5:httpcore5-h2 to v5.4.1 ([#45](https://github.com/apify/apify-camunda-integration/pull/45)) ([23eaa92](https://github.com/apify/apify-camunda-integration/commit/23eaa92e5e5e698202db28b74fa18fec13cb01cb)) by [@renovate[bot]](https://github.com/renovate[bot])
- **deps:** Update version.connectors to v8.8.8 ([#22](https://github.com/apify/apify-camunda-integration/pull/22)) ([5a60e32](https://github.com/apify/apify-camunda-integration/commit/5a60e323578a58518fe17fac24fe11f8c532c15a)) by [@renovate[bot]](https://github.com/renovate[bot])
- **deps:** Update dependency org.apache.httpcomponents.core5:httpcore5 to v5.4.2 ([#64](https://github.com/apify/apify-camunda-integration/pull/64)) ([dea156e](https://github.com/apify/apify-camunda-integration/commit/dea156e9955c0dc5b580e4223643f8d4595bb4a6)) by [@renovate[bot]](https://github.com/renovate[bot])
- **deps:** Update dependency org.apache.httpcomponents.core5:httpcore5-h2 to v5.4.2 ([#65](https://github.com/apify/apify-camunda-integration/pull/65)) ([f8b1b02](https://github.com/apify/apify-camunda-integration/commit/f8b1b02812722f7a45fe8e372854aeb9bb8a1179)) by [@renovate[bot]](https://github.com/renovate[bot])
- Correct documentationRef and wire Apify icon ([58b7b49](https://github.com/apify/apify-camunda-integration/commit/58b7b496a92e31cbbff256fc56c3be1639ae0893)) by [@daveomri](https://github.com/daveomri)
- Standardize engines.camunda to ^8.8 and add outbound documentationRef ([4f6eda6](https://github.com/apify/apify-camunda-integration/commit/4f6eda614d120b8ac09d41f0626401febe704e3e)) by [@daveomri](https://github.com/daveomri)
- Unify documentationRef ([b6a0fe2](https://github.com/apify/apify-camunda-integration/commit/b6a0fe2d1ffe6cb8621492936862f6258bd6a3ff)) by [@daveomri](https://github.com/daveomri)
- Correct license declaration to Apache-2.0 ([2b8ace7](https://github.com/apify/apify-camunda-integration/commit/2b8ace7aaade4a6fb9421405b523f825e8627306)) by [@daveomri](https://github.com/daveomri)
- Declare 512x512 intrinsic size on source SVG ([55641b3](https://github.com/apify/apify-camunda-integration/commit/55641b3f0ab3aa8427c1efa0efa0e594248adc43)) by [@daveomri](https://github.com/daveomri)
- Update maven-shade-plugin varsion to 3.6.0 and improve jar filename ([8a38277](https://github.com/apify/apify-camunda-integration/commit/8a382775becdfa4705e080fa17a3fb4443ac896c)) by [@daveomri](https://github.com/daveomri)
- Improve error handling in apifyfunction ([7b7a985](https://github.com/apify/apify-camunda-integration/commit/7b7a9856cab852790da59bbb832b49ebc9cf7342)) by [@daveomri](https://github.com/daveomri)
- Unified the message start connector with others ([c9429b4](https://github.com/apify/apify-camunda-integration/commit/c9429b4ef784f193d0b6c3d2094b3bc1d3250e9e)) by [@daveomri](https://github.com/daveomri)
- Update element templates to stadardize webhook and resource configuration fields ([8ec79b2](https://github.com/apify/apify-camunda-integration/commit/8ec79b2e2aacb376311a0a2eb357f7b701f64b2c)) by [@daveomri](https://github.com/daveomri)
- Add dependency on release_metadata for build_and_test job in release workflow ([71f5323](https://github.com/apify/apify-camunda-integration/commit/71f532369988533b8150d16519271ac33254d026)) by [@daveomri](https://github.com/daveomri)
- Update pom.xml deps and localconnectorruntime to work across versions of spring boot ([200ea0d](https://github.com/apify/apify-camunda-integration/commit/200ea0d30a17b57edc143e79ae0cda55397a5cfe)) by [@daveomri](https://github.com/daveomri)
- Add exclusion for slf4j-api in httpclient5 dependency to resolve conflicts ([9019d11](https://github.com/apify/apify-camunda-integration/commit/9019d11e549d3965b8d30a12bbd77e142de6386b)) by [@daveomri](https://github.com/daveomri)
- Update pom.xml with connector sdk version and improve comments; remove obsolete integration test ([6407065](https://github.com/apify/apify-camunda-integration/commit/64070658fcdecd31d80752f1002142352ad53c60)) by [@daveomri](https://github.com/daveomri)

### Fix

- Upgrading Camunda version ([#36](https://github.com/apify/apify-camunda-integration/pull/36)) ([863e5a5](https://github.com/apify/apify-camunda-integration/commit/863e5a5b9c5d5aa6df8938bcc95874ddf7540c81)) by [@daveomri](https://github.com/daveomri)


# Changelog

All notable changes to this project will be documented in this file.