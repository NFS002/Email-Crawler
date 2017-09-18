# Email-Crawler
This is an email address web crawler written in java, the program takes a single command line argument, a url on which to start the crawler off,
from then on the crawler find all email addresses on that site, makes a note of them, and also follows sll links on that page to 
another url. When the program finishes, the email adresses are all printed to a file. The program has other settings also, such as
a timeout feature which ends the prgram after a certain number of minutes. If no timeout is set, the program would probably
continue indefinetely, there can be no guarantee either that each site visited contains any email addresses. Another feature
determines how thorough a search the program performs. This feature has three settings, 'Deep','SuperDomain', or 'Current'
and determines whether the program should only search the current page, all pages within that domain, or any pages. This is 
another way of terminating the program and ensuring it does not run indefinetely.