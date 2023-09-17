#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <dirent.h>
#include <string.h>

int stringendswith(const char *s, const char *t)
{
    size_t slen = strlen(s);
    size_t tlen = strlen(t);
    if (tlen > slen) return 1;
    return strcmp(s + slen - tlen, t);
}

int main(void) {
    struct dirent *de;  // Pointer for directory entry
  
    // opendir() returns a pointer of DIR type. 
    DIR *dr = opendir(".");
  
    if (dr == NULL)  // opendir returns NULL if couldn't open directory
    {
        printf("Could not open current directory" );
        return 0;
    }
  
    while ((de = readdir(dr)) != NULL) {
        printf("%s\n", de->d_name);
        if (!stringendswith(de->d_name, ".txt")) {
            FILE* toCopy = fopen(de->d_name, "r");
            FILE* toCopyTo = fopen(strcat(de->d_name, ".threaded"), "w");
            fseek(toCopy, 0L, SEEK_END);
            size_t fileSize = ftell(toCopy);
            rewind(toCopy);
            unsigned char* buf = (unsigned char*) malloc(fileSize);
            for (size_t n = 0; n < fileSize; n++) {
                fread(buf, 1, 1, toCopy);
            }
            for (int i =0; i < 5; i++) {
                fwrite(buf, fileSize, 1, toCopyTo);
                char newLine = '\n';
                fwrite(&newLine, sizeof(char), 1, toCopyTo);
            }
            fclose(toCopy);
            fclose(toCopyTo);
        }
    }
  
    closedir(dr);    
    return 0;
}
