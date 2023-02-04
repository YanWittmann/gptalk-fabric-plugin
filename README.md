# GPTalk - a fabric server plugin

A fabric plugin that uses OpenAi's GPT models to create conversations with any mob on a variety of subjects.

**This plugin is a proof of concept only. I will not be maintaining or continuing active development of this plugin.**

## Usage

### Properties file

The [configuration file](src/main/resources/mod.properties) contains a list of the available properties.  
This includes:

- `openai.key` The OpenAi API key (use the `OPENAI_KEY` environment variable when building the plugin for purposes other
  than testing)
- `openai.model` The OpenAi model to use, from a list of the currently available models
- `gptalk.active` Whether fake responses should be generated to test the plugin instead of using the OpenAi API to prevent costs
- `gptalk.analytics` An analytics URL to send data to

### Analytics

You can keep track of the conservations that are being helt by the players on your server by setting the
`gptalk.analytics` property to a URL. This URL points to a PHP script that will receive the data and store it in a MySQL
database.

Here's a more detailed description on how to set the analytics up:

1. Create a MySQL database and a user with access to it
2. Set your database credentials in the [analytics_scripts/db_login_data.php](analytics_scripts/db_login_data.php) file
3. Copy the files in [analytics_scripts](analytics_scripts) to a directory on your webserver
4. Enter the URL to the [analytics_scripts/talk_event.php](analytics_scripts/talk_event.php) file in the
   `gptalk.analytics` property

Requests will now be sent to the analytics server. You can view the data by visiting the
[analytics_scripts/conversation_list.php](analytics_scripts/conversation_list.php) file.
